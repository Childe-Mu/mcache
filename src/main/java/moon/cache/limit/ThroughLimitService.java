package moon.cache.limit;

import com.google.common.util.concurrent.RateLimiter;
import moon.cache.autoconfigure.CacheProperties;

/**
 * 缓存穿透限流服务
 * （可以考虑使用Sentinal或者自己实现限流服务）
 *
 * @author moon
 */
public class ThroughLimitService {

    private final CacheProperties properties;

    /**
     * RateLimiter
     */
    private RateLimiter rateLimiter;

    public ThroughLimitService(CacheProperties properties) {
        this.properties = properties;
        rateLimiter = RateLimiter.create(properties.getThroughLimitPermitsPerSecond());
    }

    /**
     * 穿透限流
     *
     * @return true: 通过 false：不通过
     */
    public boolean throughLimit() {
        //穿透限流开关关闭
        if (!properties.getThroughLimitSwitch()) {
            return true;
        }
        return rateLimiter.tryAcquire();
    }
}