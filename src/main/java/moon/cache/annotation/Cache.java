package moon.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 二级缓存注解
 *
 * @author moon
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cache {
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
     * 缓存数据的类
     * 必填
     *
     * @return 缓存数据的类
     */
    Class<?> clazz();

    /**
     * 过期时间 单位秒
     *
     * @return 返回过期时间
     */
    long ttl() default 30 * 60;
}
