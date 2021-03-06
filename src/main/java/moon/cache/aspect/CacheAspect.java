package moon.cache.aspect;

import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import moon.cache.annotation.Cache;
import moon.cache.annotation.CacheEvict;
import moon.cache.autoconfigure.CacheProperties;
import moon.cache.common.consts.NumConst;
import moon.cache.common.exception.CacheException;
import moon.cache.limit.ThroughLimitService;
import moon.cache.proxy.LocalCacheProxy;
import moon.cache.proxy.RedisCacheProxy;
import org.apache.commons.lang.ArrayUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanCopier;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

    /**
     * 配置
     */
    private final CacheProperties properties;

    /**
     * Spring EL表达式解析器
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 获取方法参数
     */
    private final LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();

    /***
     * 缓存参数名称
     */
    private final Map<String, String[]> parameterNamesCache = new ConcurrentHashMap<>();

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
     * @param joinPoint 连接点
     * @return 执行结果
     * @throws Throwable 异常信息
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
            Cache cache = getCacheAnnotation(method);

            // 获取缓存的key
            cacheKey = getCacheKey(cache, joinPoint);
            long ttl = cache.ttl();

            // 一级缓存获取
            String domain = cache.domain();
            Object result = localCacheProxy.getValue(domain, cacheKey);
            if (Objects.nonNull(result)) {
                log.info("[localCache hit] cacheKey={}", cacheKey);
                // 进行bean copy，防止直接修改本地缓存
                return copyResult(cache, result);
            }

            // 二级缓存获取
            result = redisCacheProxy.get(domain, cacheKey);
            if (Objects.nonNull(result)) {
                log.info("[redisCache hit] cacheKey={}", cacheKey);
                // 写入一级缓存
                localCacheProxy.putValue(domain, cacheKey, result);
                return result;
            }

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
                redisCacheProxy.set(domain, cacheKey, result, ttl, TimeUnit.SECONDS);
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
    public void cacheEvict(JoinPoint joinPoint) {
        log.info("cacheEvict_enabled={}", properties.getEnabled());
        if (!properties.getEnabled()) {
            return;
        }
        String redisKey = null;
        try {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Method method = methodSignature.getMethod();
            CacheEvict cacheEvict = getCacheEvictAnnotation(method);
            Assert.notNull(cacheEvict, "moon.cache.aspect.CacheAspect.cacheEvict() 获取注解失败！");
            String domain = cacheEvict.domain();
            boolean evictAfterTranCommit = cacheEvict.evictAfterTranCommit();
            redisKey = getCacheEvictKey(cacheEvict, joinPoint);

            // 如果事务后清除，且当前事务开启
            if (evictAfterTranCommit && TransactionSynchronizationManager.isSynchronizationActive()) {
                final String finalRedisKey = redisKey;
                final String finalDomain = domain;
                // 为当前线程注册一个新的事务同步，通常由资源管理代码调用。
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
     * 复制结果，用于使用本地缓存的时候，以防外部逻辑直接修改缓存对象
     *
     * @param cache  缓存注解
     * @param result 要复制的结果
     * @return 复制后的对象
     */
    private Object copyResult(Cache cache, Object result) {
        try {
            Class<?> clazz = cache.clazz();
            BeanCopier copier = BeanCopier.create(clazz, clazz, false);
            Object copy = clazz.newInstance();
            copier.copy(result, copy, null);
            return copy;
        } catch (InstantiationException | IllegalAccessException e) {
            log.error("复制本地缓存结果异常:", e);
            throw new CacheException("复制结果异常:", e);
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
    private String getCacheEvictKey(CacheEvict cacheEvict, JoinPoint joinPoint) {
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
        // 获取方法所有参数名称
        String methodLongName = joinPoint.getSignature().toLongString();
        // 获取被注解的方法
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        // 参数名称数组
        Function<String, String[]> function = o -> discoverer.getParameterNames(method);
        String[] parameterNames = parameterNamesCache.computeIfAbsent(methodLongName, function);
        if (ArrayUtils.isEmpty(parameterNames)) {
            log.error("找不到方法对应的参数名称列表");
            throw new CacheException("找不到方法对应的参数名称列表");
        }
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
     * 获取Cache注解
     *
     * @param method 切面方法
     * @return Cache注解
     */
    private Cache getCacheAnnotation(Method method) {
        try {
            return method.getAnnotation(Cache.class);
        } catch (Exception e) {
            log.error("getCacheAnnotation from method ex:", e);
            throw new CacheException("getCacheAnnotation from method fail");
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
            log.error("getCacheEvictAnnotation from method ex:", e);
            throw new CacheException("getCacheEvictAnnotation from method fail");
        }
    }
}
