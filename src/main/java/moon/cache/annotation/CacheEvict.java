package moon.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 清理缓存注解
 *
 * @author moon
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvict {
    /**
     * 业务领域（缓存key的前缀）
     *
     * @return 业务领域
     */
    String domain() default "mcache";

    /**
     * key数组（缓存key的组成部分，拼接在domain后面）
     *
     * @return key组成数组
     */
    String[] keys() default {};

    /**
     * 是否在事务提交后清除缓存
     *
     * @return 是否在事务提交后清除缓存，true：是，false：否
     */
    boolean evictAfterTranCommit() default false;

}
