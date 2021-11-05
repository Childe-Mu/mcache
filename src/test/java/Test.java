import moon.cache.annotation.Cache;
import moon.cache.annotation.CacheEvict;

/**
 * 缓存使用范例
 *
 * @author moon
 */
public class Test {

    @Cache(domain = "mcache", keys = {"#id", "#param.code"}, clazz = Result.class)
    public Result testCache(Long id, Param param) {
        return new Result();
    }

    @CacheEvict(domain = "mcache", keys = {"#id", "#param.code"}, evictAfterTranCommit = true)
    public void testCacheEvict(Long id, Param param) {
    }
}
