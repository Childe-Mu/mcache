package moon.cache.config;

/**
 * 缓存配置
 *
 * @author moon
 */
public class CacheConfig {

    /**
     * domain
     */
    private String domain;
    /**
     * 缓存写后过期时间，单位(秒)
     */
    private int expireAfterWrite;
    /**
     * 缓存最大容量
     */
    private int maxSize;

    /**
     * 默认无参构造方法
     */
    public CacheConfig() {
    }

    /**
     * 构造方法
     *
     * @param domain           domain名称
     * @param expireAfterWrite 缓存写后过期时间
     * @param maxSize          缓存最大长度
     */
    public CacheConfig(String domain, int expireAfterWrite, int maxSize) {
        this.domain = domain;
        this.expireAfterWrite = expireAfterWrite;
        this.maxSize = maxSize;
    }

    public int getExpireAfterWrite() {
        return expireAfterWrite;
    }

    public void setExpireAfterWrite(int expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}