package moon.cache.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import moon.cache.config.CacheConfig;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存
 *
 * @author moon
 */
@Slf4j
public class LocalCacheInstance {

    /**
     * 缓存写后过期时间，单位(秒)
     */
    private static final Integer DEFAULT_EXPIRE_AFTER_WRITE = 300;

    /**
     * 缓存最大容量
     */
    private static final Integer DEFAULT_MAXSIZE = 10000;

    /**
     *
     */
    private static final Integer DEFAULT_LOCAL_CACHE_MAP_MAXSIZE = 1000;

    /**
     * 默认domain
     */
    private static final String DEFAULT_DOMAIN_NAME = "DEFAULT";

    /**
     * 本地缓存实例
     */
    private static final ConcurrentHashMap<String, Cache<String, Object>> LOCAL_CACHE_MAP = new ConcurrentHashMap<>();

    /**
     * 本地缓存配置映射
     */
    private ConcurrentMap<String, CacheConfig> localCacheConfigMap;

    /**
     * 本地缓存单例
     */
    private static LocalCacheInstance instance;

    private LocalCacheInstance(ConcurrentMap<String, CacheConfig> localCacheConfigMap) {
        this.localCacheConfigMap = localCacheConfigMap;
        if (this.localCacheConfigMap == null) {
            this.localCacheConfigMap = new ConcurrentHashMap<>();
        }
        this.localCacheConfigMap.put(DEFAULT_DOMAIN_NAME, new CacheConfig(DEFAULT_DOMAIN_NAME, DEFAULT_EXPIRE_AFTER_WRITE, DEFAULT_MAXSIZE));
    }

    /**
     * 获取本地缓存单例
     *
     * @param localCacheConfigMap 本地缓存配置映射
     * @return 本地缓存单例
     */
    public static LocalCacheInstance getInstance(ConcurrentMap<String, CacheConfig> localCacheConfigMap) {
        LocalCacheInstance localInstance = instance;
        if (localInstance == null) {
            synchronized (LocalCacheInstance.class) {
                localInstance = instance;
                if (localInstance == null) {
                    localInstance = new LocalCacheInstance(localCacheConfigMap);
                    //初始化默认缓存空间
                    localInstance.getCache(DEFAULT_DOMAIN_NAME);
                    instance = localInstance;
                }
            }
        }
        return localInstance;
    }

    /**
     * 设置缓存
     *
     * @param domain domain
     * @param key    key
     * @param value  value
     */
    public void putValue(String domain, String key, Object value) {
        try {
            getCache(domain).put(key, value);
        } catch (Exception e) {
            log.error("设置缓存发生异常，key: " + key, e);
            throw e;
        }
    }

    /**
     * 获取缓存数据
     *
     * @param domain domain
     * @param key    key
     * @return 缓存数据
     */
    public Object getValue(String domain, String key) {
        try {
            long start = System.nanoTime();
            Cache<String, Object> cache = getCache(domain);
            long end1 = System.nanoTime();
            Object value = cache.getIfPresent(key);
            long end2 = System.nanoTime();
            log.debug("key={} cost1={} cost2={}", key, (end1 - start), (end2 - start));
            return value;
        } catch (Exception e) {
            log.error("获取缓存发生异常，key: " + key, e);
            throw e;
        }
    }

    /**
     * 获取缓存数据，有默认值
     *
     * @param domain       domain
     * @param key          key
     * @param defaultValue defaultValue
     * @return 缓存数据，有默认值
     */
    public Object getValueOrDefault(String domain, String key, Object defaultValue) {
        try {
            Object value = getCache(domain).getIfPresent(key);
            return Objects.isNull(value) ? defaultValue : value;
        } catch (Exception e) {
            log.error("获取缓存发生异常，key: " + key, e);
            return defaultValue;
        }
    }

    /**
     * 删除缓存
     *
     * @param domain domain
     * @param key    key
     */
    public void deleteValue(String domain, String key) {
        try {
            getCache(domain).invalidate(key);
        } catch (Exception e) {
            log.error("删除缓存发生异常，key: " + key, e);
            throw e;
        }
    }

    /**
     * 清除缓存
     */
    public void clearAll(String domain) {
        getCache(domain).invalidateAll();
    }

    /**
     * 获取缓存统计
     *
     * @return CacheStats
     */
    public CacheStats getCacheStats(String domain) {
        return getCache(domain).stats();
    }

    /**
     * 获取cache实例
     *
     * @param domain 业务领域
     * @return cache实例
     */
    private Cache<String, Object> getCache(String domain) {
        Cache<String, Object> cache = LOCAL_CACHE_MAP.get(domain);
        if (Objects.nonNull(cache)) {
            return cache;
        }
        synchronized (LocalCacheInstance.class) {
            cache = LOCAL_CACHE_MAP.get(domain);
            if (cache == null) {
                int mapSize = LOCAL_CACHE_MAP.size();
                if (mapSize > DEFAULT_LOCAL_CACHE_MAP_MAXSIZE) {
                    log.error("LOCAL_CACHE_MAP 超过maxSize={}最大限制，将使用默认缓存空间", DEFAULT_LOCAL_CACHE_MAP_MAXSIZE);
                    cache = LOCAL_CACHE_MAP.get(DEFAULT_DOMAIN_NAME);
                } else {
                    cache = LOCAL_CACHE_MAP.computeIfAbsent(domain, o -> createCache(domain));
                }
            }
        }
        return cache;
    }

    /**
     * 创建本地缓存
     *
     * @param domain 业务领域
     * @return 本地缓存
     */
    private Cache<String, Object> createCache(String domain) {
        Cache<String, Object> cache;
        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        CacheConfig cacheConfig = localCacheConfigMap.get(domain);
        int maxSize = Objects.isNull(cacheConfig) ? DEFAULT_MAXSIZE : cacheConfig.getMaxSize();
        int expireAfterWrite = Objects.isNull(cacheConfig) ? DEFAULT_EXPIRE_AFTER_WRITE : cacheConfig.getExpireAfterWrite();
        log.debug("getCache domain={} expireAfterWrite={} maxSize={}", domain, expireAfterWrite, maxSize);
        // 设置缓存最大容量
        if (maxSize > 0) {
            cacheBuilder.maximumSize(maxSize);
        }
        // 设置缓存写后过期时间
        if (expireAfterWrite > 0) {
            cacheBuilder.expireAfterWrite(expireAfterWrite, TimeUnit.SECONDS);
        }
        // 待补充统计缓存命中率
        // 缓存移除通知
        cacheBuilder.removalListener(new LocalCacheRemoveListener(domain));
        cache = cacheBuilder.build();
        return cache;
    }
}
