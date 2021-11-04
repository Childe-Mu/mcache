package moon.cache.proxy;

import lombok.extern.slf4j.Slf4j;
import moon.cache.autoconfigure.CacheProperties;
import moon.cache.common.exception.CacheException;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * redis锁代理
 *
 * @author moon
 */
@Slf4j
public class RedisCacheProxy {

    /**
     * 访问redis失败时的重试次数
     */
    public static final int RETRY_TIMES = 2;

    /**
     * 当前服务实例的redis二级缓存是否可用
     */
    private final AtomicBoolean enabled;

    /**
     * 当前服务实例的自动降级时间
     */
    private final AtomicLong disabledTime;

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


    public RedisCacheProxy(CacheProperties properties,
                           RedisTemplate<String, Object> cacheRedisTemplate,
                           List<RedisDeleteKeyListener> deleteKeyListeners) {
        if (Objects.isNull(properties.getRedisEnabled())) {
            throw new CacheException("未配置mcache.redis.group值");
        }
        this.enabled = new AtomicBoolean(true);
        this.disabledTime = new AtomicLong(-1);
        this.properties = properties;
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.deleteKeyListeners = deleteKeyListeners;
    }

    /**
     * 从redis获取数据
     *
     * @param key key
     * @return Object
     */
    public Object get(String domain, String key) {
        if (!isSwitchOn(domain)) {
            return null;
        }
        AtomicReference<Object> result = new AtomicReference<>();
        Runnable task = () -> result.set(cacheRedisTemplate.opsForValue().get(key));
        runWithRetryTimes(task, RETRY_TIMES);
        return result.get();
    }

    /**
     * redis存值
     *
     * @param domain  domain
     * @param key     key
     * @param value   value
     * @param timeout timeout
     * @param unit    unit
     * @param <V>     <V>
     */
    public <V> void set(String domain, String key, V value, long timeout, TimeUnit unit) {
        if (isSwitchOn(domain)) {
            Runnable task = () -> cacheRedisTemplate.opsForValue().set(key, value, timeout, unit);
            runWithRetryTimes(task, RETRY_TIMES);
        }
    }

    /**
     * 清除缓存
     *
     * @param domain domain
     * @param key    redis key
     */
    public void delete(String domain, String key) {
        Runnable task = () -> cacheRedisTemplate.delete(key);
        runWithRetryTimes(task, RETRY_TIMES);
        for (RedisDeleteKeyListener listener : deleteKeyListeners) {
            try {
                listener.delete(domain, key);
            } catch (Exception e) {
                log.error("执行redis 删除key回调失败", e);
            }
        }
    }

    /**
     * 判断开关是否开启.
     *
     * @param domain domain
     * @return boolean
     */
    private boolean isSwitchOn(String domain) {
        return properties.getRedisEnabled() && properties.getRedisCacheDomainSwitch(domain);
    }

    /**
     * 重试指定的重试次数
     *
     * @param task       重试任务
     * @param retryTimes 重试次数
     */
    public void runWithRetryTimes(Runnable task, int retryTimes) {
        // 重试次数已到达，则触发降级。修改开关状态为关闭，并记录关闭时间
        if (retryTimes < 0) {
            enabled.set(false);
            disabledTime.compareAndSet(-1, System.currentTimeMillis());
            return;
        }

        if (needRunTask()) {
            try {
                task.run();
                // 访问 redis 成功，修改开关状态为打开，并清除关闭时间信息
                enabled.set(true);
                disabledTime.set(-1);
            } catch (Exception e) {
                log.info("访问redis出错", e);
                runWithRetryTimes(task, --retryTimes);
            }
        }
    }

    /**
     * 当前是否需要重新执行redis操作任务
     *
     * @return true：是，false：否
     */
    private boolean needRunTask() {
        if (enabled.get() || disabledTime.get() == -1) {
            return true;
        }
        // 重试间隔
        long retryPeriod = properties.getRedisLocalRetryPeriod() * 60 * 1000L;
        // 当前时间离上次失败时间超过10分钟，则可以重试
        return System.currentTimeMillis() - disabledTime.get() > retryPeriod;
    }
}
