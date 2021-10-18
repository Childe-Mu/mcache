package moon.cache.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存
 * @author moon
 */
public class LocalCacheInstance {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCacheInstance.class);

    /**
     * 缓存写后过期时间，单位(秒)
     */
    private static final Integer DEFAULT_EXPIREAFTERWRITE = 300;
    /**
     * 缓存最大容量
     */
    private static final Integer DEFAULT_MAXSIZE = 10000;
    /**
     *
     */
    private static final Integer DEFAULT_LOCALCACHEMAP_MAXSIZE = 1000;
    /**
     * 默认domain
     */
    private static final String DEFAULT_DOMAIN_NAME = "DEFAULT";
    /**
     * 本地缓存示例
     */
    private static final ConcurrentHashMap<String, Cache> LOCAL_CACHE_MAP = new ConcurrentHashMap<>();
    /**
     * 缓存配置
     */
    private ConcurrentHashMap<String, CacheConfig> localCacheConfigMap;

    private static volatile LocalCacheInstance instance;

    private LocalCacheInstance(ConcurrentHashMap<String, CacheConfig> localCacheConfigMap) {
        this.localCacheConfigMap = localCacheConfigMap;
        if (this.localCacheConfigMap == null) {
            this.localCacheConfigMap = new ConcurrentHashMap<>();
        }
        this.localCacheConfigMap.put(DEFAULT_DOMAIN_NAME, new CacheConfig(DEFAULT_DOMAIN_NAME, DEFAULT_EXPIREAFTERWRITE, DEFAULT_MAXSIZE));
    }

    public static LocalCacheInstance getInstance(ConcurrentHashMap<String, CacheConfig> localCacheConfigMap) {
        if (instance == null) {
            synchronized (LocalCacheInstance.class) {
                if (instance == null) {
                    instance = new LocalCacheInstance(localCacheConfigMap);
                    //初始化默认缓存空间
                    instance.getCache(DEFAULT_DOMAIN_NAME);
                }
            }
        }
        return instance;
    }

    /**
     * 设置缓存
     *
     * @param domain domain
     * @param key key
     * @param value value
     * @throws Exception Exception
     */
    public void putValue(String domain, String key, Object value) throws Exception {
        try {
            getCache(domain).put(key, value);
        } catch (Exception e) {
            LOGGER.error("设置缓存发生异常，key: " + key, e);
            throw e;
        }
    }

    /**
     * 获取缓存数据
     *
     * @param domain domain
     * @param key key
     * @return Object
     * @throws Exception Exception
     */
    public Object getValue(String domain, String key) throws Exception {
        try {
            long start = System.nanoTime();
            Cache<String, Object> cache = getCache(domain);
            long end1 = System.nanoTime();
            Object obj = cache.getIfPresent(key);
            long end2 = System.nanoTime();
            LOGGER.debug("key={} cost1={} cost2={}", key, (end1 - start), (end2 - start));
            return obj;
        } catch (Exception e) {
            LOGGER.error("获取缓存发生异常，key: " + key, e);
            throw e;
        }
    }

    /**
     * 获取缓存数据，有默认值
     *
     * @param domain domain
     * @param key key
     * @param defaultValue defaultValue
     * @return
     */
    public Object getValueOrDefault(String domain, String key, Object defaultValue) {
        try {
            return getCache(domain).getIfPresent(key);
        } catch (Exception e) {
            LOGGER.error("获取缓存发生异常，key: " + key, e);
            return defaultValue;
        }
    }

    /**
     * 删除缓存
     *
     * @param domain domain
     * @param key key
     * @throws Exception Exception
     */
    public void deleteValue(String domain, String key) throws Exception {
        try {
            getCache(domain).invalidate(key);
        } catch (Exception e) {
            LOGGER.error("删除缓存发生异常，key: " + key, e);
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
     * @param domain domain
     * @return Cache
     */
    private Cache<String, Object> getCache(String domain) {
        Cache<String, Object> cache = LOCAL_CACHE_MAP.get(domain);
        if (cache != null) {
            return cache;
        }
        synchronized (LocalCacheInstance.class) {
            cache = LOCAL_CACHE_MAP.get(domain);
            if (cache == null) {
                int mapSize = LOCAL_CACHE_MAP.size();
                if (mapSize > DEFAULT_LOCALCACHEMAP_MAXSIZE) {
                    LOGGER.error("LOCAL_CACHE_MAP 超过maxSize={}最大限制，将使用默认缓存空间", DEFAULT_LOCALCACHEMAP_MAXSIZE);
                    cache = LOCAL_CACHE_MAP.get(DEFAULT_DOMAIN_NAME);
                } else {
                    Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
                    CacheConfig cacheConfig = localCacheConfigMap.get(domain);
                    Integer maxSize = cacheConfig == null ? DEFAULT_MAXSIZE : cacheConfig.getMaxSize();
                    Integer expireAfterWrite = cacheConfig == null ? DEFAULT_EXPIREAFTERWRITE : cacheConfig.getExpireAfterWrite();
                    LOGGER.debug("getCache domain={} expireAfterWrite={} maxSize={}", domain, expireAfterWrite, maxSize);
                    //设置缓存最大容量
                    if (maxSize > NumConst.NUMBER_0) {
                        cacheBuilder = cacheBuilder.maximumSize(maxSize);
                    }
                    //设置缓存写后过期时间
                    if (expireAfterWrite > NumConst.NUMBER_0) {
                        cacheBuilder = cacheBuilder.expireAfterWrite(expireAfterWrite, TimeUnit.SECONDS);
                    }
                    //待补充统计缓存命中率
                    //缓存移除通知
                    cacheBuilder.removalListener(new LocalCacheRemoveListener(domain));
                    cache = cacheBuilder.build();
                    LOCAL_CACHE_MAP.put(domain, cache);
                }
            }
        }
        return cache;
    }
}
