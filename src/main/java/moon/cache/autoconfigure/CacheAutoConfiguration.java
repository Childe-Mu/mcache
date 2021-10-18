package moon.cache.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import moon.cache.aspect.CacheAspect;
import moon.cache.limit.ThroughLimitService;
import moon.cache.proxy.LocalCacheProxy;
import moon.cache.proxy.RedisCacheProxy;
import moon.cache.proxy.RedisDeleteKeyListener;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.Resource;
import java.util.List;

/**
 * CacheAutoConfiguration
 * @author moon
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "mcahe", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {
    @Resource
    private CacheProperties cacheProperties;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 通用组件redis数据源配置，mcahe.redis-group未配置时生效
     */
    @Value("${component.redis.cluster-name:}")
    private String componentRedisClusterName;

    @Bean
    public RedisCacheProxy redisCacheProxy(@Qualifier("cacheRedisTemplate") RedisTemplate<String, Object> cacheRedisTemplate,
                                           List<RedisDeleteKeyListener> deleteKeyListenerList) {
        final RedisCacheProxy redisCacheProxy = new RedisCacheProxy(cacheProperties, cacheRedisTemplate);
        redisCacheProxy.addDeleteKeyListener(deleteKeyListenerList);
        return redisCacheProxy;
    }

    @Bean
    public LocalCacheProxy localCacheProxy() {
        return new LocalCacheProxy(cacheProperties);
    }

    @Bean
    @ConditionalOnMissingBean(CacheAspect.class)
    public CacheAspect l2CacheAspect() {
        return new CacheAspect(cacheProperties);
    }

    @Bean
    public ThroughLimitService throughLimitService() {
        return new ThroughLimitService(cacheProperties);
    }

    @Bean(name = "cacheRedisTemplate")
    public RedisTemplate<String, Object> cacheRedisTemplate() {
        final String redisGroup = this.getRedisGroup();
        if (StringUtils.isBlank(redisGroup)) {
            log.error("请检查mcahe的redis数据源名称是否配置");
            throw new RuntimeException("请检查mcahe的redis数据源名称【mcahe.redis-group】是否配置");
        }
        //根据已有的redisTemplate的连接创建redisTemplate，架构redis默认为各个redis数据源创建了redisTemplate
        final String srcBeanName = redisGroup + "RedisTemplate";
        final RedisTemplate srcTemplate;
        try {
            srcTemplate = applicationContext.getBean(srcBeanName, RedisTemplate.class);
        } catch (Exception e) {
            log.error("redisTemplateBeanName={}，获取bean实例失败",srcBeanName);
            throw e;
        }
        RedisConnectionFactory factory = srcTemplate.getConnectionFactory();
        if (factory == null) {
            log.error("redisTemplateBeanName={}，redisTemplate.getConnectionFactory()为空", srcBeanName);
            throw new RuntimeException("redisTemplate.getConnectionFactory()为空");
        }
        RedisTemplate<String, Object> targetTemplate = new RedisTemplate<>();
        targetTemplate.setConnectionFactory(factory);
        targetTemplate.setValueSerializer(new HessianSerializer());
        targetTemplate.setKeySerializer(new StringRedisSerializer());
        targetTemplate.setHashKeySerializer(new StringRedisSerializer());
        targetTemplate.setHashValueSerializer(new HessianSerializer());
        targetTemplate.afterPropertiesSet();
        return targetTemplate;
    }

    private String getRedisGroup() {
        //优先取redisGroup
        String redisGroup = cacheProperties.getRedisGroup();
        if (StringUtils.isBlank(redisGroup)) {
            return componentRedisClusterName;
        }
        return redisGroup;
    }

    @Bean(name = "cacheValueOperations")
    public ValueOperations<String, Object> valueOperations(@Qualifier("cacheRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForValue();
    }
}
