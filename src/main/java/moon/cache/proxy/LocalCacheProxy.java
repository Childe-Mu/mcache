package moon.cache.proxy;


import moon.cache.autoconfigure.CacheProperties;

/**
 * 本地缓存代理
 *
 * @author moon
 */
public class LocalCacheProxy {

    private final CacheProperties properties;

    /**
     * 本地缓存
     */
    private final LocalCacheInstance localCache;

    public LocalCacheProxy(CacheProperties properties) {
        this.properties = properties;
        this.localCache = LocalCacheInstance.getInstance(properties.getLocalCacheConfigList());
    }

    /**
     * 获取数据
     *
     * @param domain domain
     * @param key    key
     * @return Object
     */
    public Object getValue(String domain, String key) {
        // 本地缓存domain级别开关
        if (!properties.getLocalEnabled() || !properties.getLocalCacheDomainSwitch(domain)) {
            return null;
        }
        return localCache.getValue(domain, key);
    }

    /**
     * 设置数据
     *
     * @param domain domain
     * @param key    key
     * @param value  value
     */
    public void putValue(String domain, String key, Object value) {
        if (properties.getLocalEnabled() && properties.getLocalCacheDomainSwitch(domain)) {
            localCache.putValue(domain, key, value);
        }
    }


    /**
     * 删除domain下的所有本地缓存
     *
     * @param domain domain
     */
    public void clearAllByDomain(String domain) {
        localCache.clearAll(domain);
    }
}