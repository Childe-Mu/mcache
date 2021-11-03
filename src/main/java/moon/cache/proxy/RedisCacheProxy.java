package moon.cache.proxy;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import moon.cache.autoconfigure.CacheProperties;
import moon.cache.common.exception.CacheException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * redis锁代理
 *
 * @author moon
 */
@Slf4j
@Service
public class RedisCacheProxy {

    /**
     * 配置信息
     */
    private final CacheProperties properties;

    /**
     * redis句柄
     */
    private final RedisTemplate<String, Object> cacheRedisTemplate;

    /**
     * 删除redis key回调监听
     */
    private final List<RedisDeleteKeyListener> deleteKeyListeners;

    public RedisCacheProxy(CacheProperties properties, RedisTemplate<String, Object> cacheRedisTemplate, List<RedisDeleteKeyListener> deleteKeyListenerList) {
        if (Objects.isNull(properties.getRedisEnabled())) {
            throw new CacheException("未配置mcache.redis.group值");
        }
        this.properties = properties;
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.deleteKeyListeners = Lists.newArrayList();
    }

    /**
     * 从redis获取数据
     *
     * @param key key
     * @return Object
     */
    public Object get(String domain, String key) {
        if (properties.getRedisEnabled() && properties.getRedisCacheDomainSwitch(domain)) {
            return cacheRedisTemplate.opsForValue().get(key);
        }
        return null;
    }

    /**
     * redis保存值
     *
     * @param key     key
     * @param value   value
     * @param timeout timeout
     * @param unit    unit
     * @param <V>     <V>
     */
    public <V> void set(String key, V value, long timeout, TimeUnit unit) {
        cacheRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 清除缓存
     *
     * @param domain domain
     * @param key    redis key
     */
    public void delete(String domain, String key) {
        cacheRedisTemplate.delete(key);
        for (RedisDeleteKeyListener listener : deleteKeyListeners) {
            try {
                listener.onDelete(domain, key);
            } catch (Exception e) {
                log.error("执行redis 删除key回调失败", e);
            }
        }
    }

    /**
     * 增加删除redis key 监听
     *
     * @param deleteKeyListenerList 监听列表
     */
    public void addDeleteKeyListener(List<RedisDeleteKeyListener> deleteKeyListenerList) {
        this.deleteKeyListeners.addAll(deleteKeyListenerList);
    }
}
