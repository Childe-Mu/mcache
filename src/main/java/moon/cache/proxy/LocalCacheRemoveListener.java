package moon.cache.proxy;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.extern.slf4j.Slf4j;

/**
 * @description: 本地缓存移除通知监听器
 * @author: likejian
 * @date: 2019.02.01 13:49
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
     * @param domain domin名称
     */
    public LocalCacheRemoveListener(String domain) {
        this.domain = domain;
    }

    @Override
    public void onRemoval(String s, Object o, RemovalCause removalCause) {
        log.debug("缓存移除 domain={}, key={}", this.domain, s);
    }
}
