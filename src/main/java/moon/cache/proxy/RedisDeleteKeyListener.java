package moon.cache.proxy;

/**
 * 删除redis key回调
 *
 * @author moon
 */
public interface RedisDeleteKeyListener {

    /**
     * 删除redis key 通知；同步执行
     *
     * @param domain domain名称
     * @param key    redis key
     */
    void delete(String domain, String key);
}
