package moon.cache.proxy;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * 本地缓存移除通知监听器
 *
 * @author moon
 */
@Slf4j
public class LocalCacheRemoveListener implements RemovalListener<String, Object> {
    /**
     * 缓存domain
     */
    private final String domain;

    /**
     * 构造方法
     *
     * @param domain domin
     */
    public LocalCacheRemoveListener(String domain) {
        this.domain = domain;
    }

    @Override
    public void onRemoval(String s, Object o, @NonNull RemovalCause removalCause) {
        log.debug("缓存移除 domain={}, key={}", this.domain, s);
    }
}
