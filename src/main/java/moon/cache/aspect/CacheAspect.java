package moon.cache.aspect;

import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import moon.cache.annotation.Cache;
import moon.cache.annotation.CacheEvict;
import moon.cache.autoconfigure.CacheProperties;
import moon.cache.common.consts.NumConst;
import moon.cache.limit.ThroughLimitService;
import moon.cache.proxy.LocalCacheProxy;
import moon.cache.proxy.RedisCacheProxy;
import moon.cache.utils.AspectUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 二级缓存AOP切入点.
 *
 * @author moon
 */
@Slf4j
@Aspect
@Order(value = 1)
public class CacheAspect {

    /**
     * 配置
     */
    private CacheProperties properties;

    /**
     * Spring EL表达式解析器
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 获取方法参数
     */
    private final LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();

    /**
     * redis缓存代理
     */
    @Autowired
    private RedisCacheProxy redisCacheProxy;

    /**
     * 本地缓存代理
     */
    @Autowired
    private LocalCacheProxy localCacheProxy;

    /**
     * 缓存穿透限流服务
     */
    @Autowired
    private ThroughLimitService throughLimitService;

    /***
     * 缓存参数名称
     */
    private final Map<String, String[]> parameterNamesCache = new ConcurrentHashMap<>();
    private final Map<String, String> methodAndParamCache = new ConcurrentHashMap<>();

    /**
     * 默认domain
     */
    private static final String DEFAULT_DOMAIN_NAME = "DEFAULT";

    /**
     * null值最大范围
     */
    private static final Integer NULL_VALUE_MAX = 10;

    /**
     * null值后缀
     */
    private static final String NULL_VALUE_SUFFIX = "_NULL";

    public CacheAspect(CacheProperties properties) {
        this.properties = properties;
    }

    /**
     * 缓存切入点
     */
    @Pointcut("@annotation(moon.cache.annotation.Cache)")
    public void cacheAspect() {
        // do nothing
    }

    /**
     * 清除缓存切入点
     */
    @Pointcut("@annotation(moon.cache.annotation.CacheEvict)")
    public void cacheEvictAspect() {
        // do nothing
    }

    /**
     * 缓存结果集环绕逻辑
     *
     * @return
     * @throws Throwable
     */
    @Around("cacheAspect()")
    public Object cache(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("enabled={}", properties.getEnabled());
        if (!properties.getEnabled()) {
            return joinPoint.proceed();
        }
        String cacheKey = null;
        try {
            // 切入点处的签名
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Method method = methodSignature.getMethod();
            Cache annotation = getCacheAnnotation(method);
            Assert.notNull(annotation, "moon.cache.aspect.CacheAspect.cache() 获取注解失败！");
            String domain = annotation.domain();

            // 获取缓存的key
            cacheKey = getCacheKey(annotation, joinPoint);
            long ttl = annotation.ttl();

            // 一级缓存获取
            Object result = localCacheProxy.getValue(domain, cacheKey);
            if (Objects.nonNull(result)) {
                log.info("[localCache hit] cacheKey={}", cacheKey);
                return result;
            }

            // 二级缓存获取
            result = redisCacheProxy.get(domain, cacheKey);
            if (Objects.nonNull(result)) {
                log.info("[redisCache hit] cacheKey={}", cacheKey);
                // 写入一级缓存
                localCacheProxy.putValue(domain, cacheKey, result);
                return result;
            }

            // todo 加入本地缓存失效，redis链接失败的问题场景处理，这种情况下，直接穿透到DB

            // 一二级缓存都查不到，进行防穿透设置
            log.info("[redisCache miss] cacheKey={}", cacheKey);
            String nullKey = cacheKey + NULL_VALUE_SUFFIX;
            Integer nullValue = (Integer) localCacheProxy.getValue(DEFAULT_DOMAIN_NAME, nullKey);
            nullValue = Objects.isNull(nullValue) ? NumConst.NUM_0 : nullValue;
            log.info("nullKey={} nullValue={}", nullKey, nullValue);
            if (nullValue > NULL_VALUE_MAX) {
                return null;
            }

            // 穿透限流
            if (!throughLimitService.throughLimit()) {
                log.info("[Through Limit] cacheKey={}", cacheKey);
                return null;
            }

            // 穿透处理
            result = joinPoint.proceed();
            if (Objects.nonNull(result)) {
                log.debug("[DB hit] {}", cacheKey);
                redisCacheProxy.set(cacheKey, result, ttl, TimeUnit.SECONDS);
                localCacheProxy.putValue(domain, cacheKey, result);
                return result;
            }

            // 数据库miss，设置防穿透null key
            log.info("[DB miss] {}", cacheKey);
            localCacheProxy.putValue(DEFAULT_DOMAIN_NAME, nullKey, ++nullValue);
            return null;
        } catch (Exception e) {
            log.error("CacheAspect缓存异常, key={}", cacheKey, e);
            throw e;
        }
    }

    /**
     * 清除缓存结果集后置逻辑
     */
    @After("cacheEvictAspect()")
    public void cacheEvict(ProceedingJoinPoint joinPoint) {
        log.info("cacheEvict_enabled={}", properties.getEnabled());
        if (!properties.getEnabled()) {
            return;
        }
        String redisKey = null;
        try {
            Method method = getMethod(joinPoint);
            List<CacheEvict> annotations = getCacheEvictAnnotations(method);
            if (annotations == null || annotations.isEmpty()) {
                return;
            }
            CacheEvict annotation = getCacheEvictAnnotation(method);
            Assert.notNull(annotation, "moon.cache.aspect.CacheAspect.cacheEvict() 获取注解失败！");
            String domain = annotation.domain();
            boolean evictAfterTranCommit = annotation.evictAfterTranCommit();
            redisKey = getCacheEvictKey(annotation, joinPoint);

            //如果事务后清除，且当前事务开启
            if (evictAfterTranCommit && TransactionSynchronizationManager.isSynchronizationActive()) {
                final String finalRedisKey = redisKey;
                final String finalDomain = domain;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        log.debug("[redisCache delete] ,cacheKey:{}", finalRedisKey);
                        redisCacheProxy.delete(finalDomain, finalRedisKey);
                    }
                });
            } else {
                log.debug("[redisCache delete] ,cacheKey:{}", redisKey);
                redisCacheProxy.delete(domain, redisKey);
            }
        } catch (Exception e) {
            log.error("CacheAspect清除缓存异常, cacheKey:{}", redisKey, e);
        }
    }


    /**
     * 获取缓存的key
     *
     * @param cache     @Cache注解信息
     * @param joinPoint 连接点
     * @return 缓存的key
     */
    private String getCacheKey(Cache cache, ProceedingJoinPoint joinPoint) {
        String domain = cache.domain();
        String[] keyTemplate = cache.keys();
        String[] keys = executeTemplate(keyTemplate, joinPoint);
        return domain + ":" + Joiner.on("_").join(keys);
    }

    /**
     * 获取清除缓存的key
     *
     * @param cacheEvict @CacheEvict注解信息
     * @param joinPoint  连接点
     * @return 缓存的key
     */
    private String getCacheEvictKey(CacheEvict cacheEvict, ProceedingJoinPoint joinPoint) {
        String domain = cacheEvict.domain();
        String[] keyTemplate = cacheEvict.keys();
        String[] keys = executeTemplate(keyTemplate, joinPoint);
        return domain + ":" + Joiner.on("_").join(keys);
    }

    /**
     * 执行表达式模板并返回结果
     *
     * @param template  需要执行的表达式模板
     * @param joinPoint 切面的切入点信息
     * @return 表达式执行结果集
     */
    private String[] executeTemplate(String[] template, JoinPoint joinPoint) {
        //获取方法所有参数名称
        String methodLongName = joinPoint.getSignature().toLongString();
        // 参数名称数组
        Function<String, String[]> function = o -> discoverer.getParameterNames(getMethod(joinPoint));
        String[] parameterNames = parameterNamesCache.computeIfAbsent(methodLongName, function);
        // SpEL 的标准计算上下文
        StandardEvaluationContext context = new StandardEvaluationContext();
        // 获取对应的参数
        Object[] args = joinPoint.getArgs();
        // 将参数添加到 SpEL 的标准计算上下文
        if (args.length == parameterNames.length) {
            for (int i = 0; i < args.length; i++) {
                // 设置参数名和对应的参数值
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        String[] result = new String[template.length];
        for (int i = 0; i < template.length; i++) {
            Expression expression = parser.parseExpression(template[i]);
            String value = expression.getValue(context, String.class);
            result[i] = value;
        }
        return result;
    }

    /**
     * 获取当前执行的方法
     *
     * @param joinPoint 连接点
     * @return 当前执行的方法
     */
    private Method getMethod(JoinPoint joinPoint) {
        String methodLongName = joinPoint.getSignature().toLongString();
        // 方法名称参数已缓存，则直接从缓存获取，反之则解析名称参数，并加入缓存
        String methodNameAndParam = methodAndParamCache.computeIfAbsent(methodLongName, o -> AspectUtils.getMethodNameAndParams(methodLongName));
        // 获取切点所在类的全部方法列表
        Method[] methods = joinPoint.getTarget().getClass().getMethods();
        for (Method method : methods) {
            String targetMethodLongName = method.toString();
            String targetMethodAndParam = methodAndParamCache.computeIfAbsent(targetMethodLongName, o -> AspectUtils.getMethodNameAndParams(targetMethodLongName));
            if (StringUtils.equals(methodNameAndParam, targetMethodAndParam)) {
                return method;
            }
        }
        return null;
    }

    /**
     * 获取Cache注解
     *
     * @param method 切面方法
     * @return Cache注解
     */
    private Cache getCacheAnnotation(Method method) {
        try {
            return method.getAnnotation(Cache.class);
        } catch (Exception e) {
            log.error("getLock from method ex:", e);
            return null;
        }
    }


    /**
     * 获取CacheEvict注解
     *
     * @param method 切面方法
     * @return CacheEvict注解
     */
    private CacheEvict getCacheEvictAnnotation(Method method) {
        try {
            return method.getAnnotation(CacheEvict.class);
        } catch (Exception e) {
            log.error("getLock from method ex:", e);
            return null;
        }
    }

    /**
     * 获取方法是的CacheEvict注解
     *
     * @return
     */
    private List<CacheEvict> getCacheEvictAnnotations(Method method) {
        return Arrays.stream(method.getAnnotations())
                .filter(CacheEvict.class::isInstance)
                .map(CacheEvict.class::cast)
                .collect(Collectors.toList());
    }
}
