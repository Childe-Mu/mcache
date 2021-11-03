package moon.cache.config;

import lombok.Getter;

/**
 * 缓存配置
 *
 * @author moon
 */
@Getter
public class CacheConfig {

    /**
     * domain
     */
    private final String domain;

    /**
     * 缓存写后过期时间，单位(秒)
     */
    private final int expireAfterWrite;

    /**
     * 缓存最大容量
     */
    private final int maxSize;

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
}