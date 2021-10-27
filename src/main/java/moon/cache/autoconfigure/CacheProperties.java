package moon.cache.autoconfigure;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import moon.cache.common.consts.NumConst;
import moon.cache.config.CacheConfig;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * mcache配置
 *
 * @author moon
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "mcache")
public class CacheProperties {

    /**
     * 应用名称
     */
    @Value("${moon.application.name}")
    private String applicationName;

    /**
     * L2缓存-组件开关
     */
    private Boolean enabled;

    /**
     * L2缓存-本地缓存开关
     */
    @Value("${${moon.application.name}.mcahe.local.switch:false}")
    private Boolean localEnabled;

    /**
     * L2缓存-本地缓存按domain开关
     */
    @Value("#{${${moon.application.name}.mcahe.local.domains.switch:new java.util.HashMap()}}")
    private Map<String, Boolean> localDomainsSwitch;

    /**
     * L2缓存-本地缓存domain配置
     */
    @Value("${${moon.application.name}.mcahe.local.domains.config:}")
    private String localDomainsConfig;

    /**
     * L2缓存-redis缓存的group
     */
    @Value("${${moon.application.name}.mcahe.redis.group:}")
    private String redisGroup;

    /**
     * L2缓存-redis缓存开关
     */
    @Value("${${moon.application.name}.mcahe.redis.enabled:true}")
    private Boolean redisEnabled;

    /**
     * L2缓存-redis缓存按domain开关
     */
    @Value("#{${${moon.application.name}.mcahe.redis.domains.switch:new java.util.HashMap()}}")
    private Map<String, Boolean> redisDomainsSwitch;

    /**
     * L2缓存-缓存穿透限流开关，默认值关闭
     */
    @Value("${${moon.application.name}.mcahe.through.limit.switch:false}")
    private Boolean throughLimitSwitch;

    /**
     * L2缓存-缓存穿透限流每秒许可数，默认值5
     */
    @Value("${${moon.application.name}.mcahe.through.limit.permits.persecond:5}")
    private Integer throughLimitPermitsPerSecond;

    /**
     * L2缓存-Zeus上报采样开关，false：全量上报，true：采样上报
     */
    @Value("${${moon.application.name}.mcahe.zeus.sampling.report.switch:false}")
    private Boolean zeusSamplingReportSwitch;
    /**
     * L2缓存-Zeus上报采样比例，取值：0-10，0：不上报，10：全部上报
     */
    @Value("${${moon.application.name}.mcahe.zeus.sampling.report.ratio:2}")
    private Integer zeusSamplingReportRatio;


    /**
     * 获取redis缓存-domain开关
     *
     * @param domain domain
     * @return Boolean
     */
    public Boolean getRedisCacheDomainSwitch(String domain) {
        Boolean domainSwitch = redisDomainsSwitch.get(domain);
        return Objects.isNull(domainSwitch) || domainSwitch;
    }

    /**
     * 获取本地缓存-domain开关
     *
     * @param domain domain
     * @return Boolean
     */
    public Boolean getLocalCacheDomainSwitch(String domain) {
        Boolean domainSwitch = localDomainsSwitch.get(domain);
        return Objects.isNull(domainSwitch) || domainSwitch;
    }

    /**
     * 获取本地缓存配置
     *
     * @return 本地缓存配置List
     */
    public ConcurrentMap<String, CacheConfig> getLocalCacheConfigList() {
        if (StringUtils.isEmpty(localDomainsConfig)) {
            return null;
        }

        String conf = JSON.parseObject(localDomainsConfig, String.class);
        if (StringUtils.isEmpty(conf)) {
            return null;
        }
        List<CacheConfig> configList = JSON.parseArray(conf, CacheConfig.class);
        ConcurrentHashMap<String, CacheConfig> localCacheConfigMap = new ConcurrentHashMap<>(NumConst.NUM_16);
        if (!CollectionUtils.isEmpty(configList)) {
            for (CacheConfig cacheConfig : configList) {
                localCacheConfigMap.put(cacheConfig.getDomain(), cacheConfig);
            }
        }
        return localCacheConfigMap;
    }
}
