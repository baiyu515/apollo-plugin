package com.lyy.fengxiao.fengxiao.apollo.plugin;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfig;
import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.spring.config.ConfigPropertySourceFactory;
import com.ctrip.framework.apollo.spring.config.PropertySourcesConstants;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.http.HttpRequest;
import com.ctrip.framework.apollo.util.http.HttpResponse;
import com.ctrip.framework.apollo.util.http.HttpUtil;
import com.ctrip.framework.foundation.internals.provider.DefaultApplicationProvider;
import com.google.gson.reflect.TypeToken;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.text.MessageFormat;
import java.util.List;

/**
 * @author fengxiao
 * @date 2020/4/29 14:17
 */
@Service
public class ApolloContextInitializer implements EnvironmentPostProcessor {

    /**
     * 配置是否执行
     */
    private final static boolean APOLLO_ENABLE = Boolean.parseBoolean(System.getProperty(ApolloConfigConstants.APOLLO_ENABLE, Boolean.TRUE.toString()));


    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        initialize(environment);
    }

    private void initialize(ConfigurableEnvironment environment) {
        if (!APOLLO_ENABLE) {
            return;
        }

        //获取对应app_id下的所有配置default、关联的公共namespace
        List<ApolloConfig> apolloConfigList = allApolloConfig();
        Assert.notEmpty(apolloConfigList, MessageFormat.format("该项目没有Apollo配置项，如无需使用Apollo，请在VM参数中配置-D{0}=false", ApolloConfigConstants.APOLLO_ENABLE));
        CompositePropertySource composite = new CompositePropertySource(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
        ConfigPropertySourceFactory configPropertySourceFactory = SpringInjector.getInstance(ConfigPropertySourceFactory.class);
        for (ApolloConfig apolloConfig : apolloConfigList) {
            Config config = ConfigService.getConfig(apolloConfig.getNamespaceName());
            composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(apolloConfig.getNamespaceName(), config));
        }
        //将加载到的namespace，添加到spring环境中
        environment.getPropertySources().addFirst(composite);
    }

    private List<ApolloConfig> allApolloConfig() {
        DefaultApplicationProvider defaultApplicationProvider = getApplicationProvider();
        String configServiceUri = getConfigServiceUri(defaultApplicationProvider);
        ServiceDTO adminService = getAdminService(configServiceUri);
        return allApolloConfig(defaultApplicationProvider, adminService);
    }

    /**
     * 获取apollo配置的config，包含自己本身的default namespace，和关联的公共namespace
     * @param defaultApplicationProvider  应用
     * @param adminService  admin service
     * @return 配置的namespace
     */
    private List<ApolloConfig> allApolloConfig(DefaultApplicationProvider defaultApplicationProvider, ServiceDTO adminService) {
        String clustersName = System.getProperty(ConfigConsts.APOLLO_CLUSTER_KEY, ConfigConsts.CLUSTER_NAME_DEFAULT);
        String namespacesUrl = MessageFormat.format(ApolloConfigConstants.NAMESPACES_URL_PATTERN, adminService.getHomepageUrl(), defaultApplicationProvider.getAppId(), clustersName);
        try {
            HttpUtil httpUtil = new HttpUtil();
            HttpRequest request = new HttpRequest(namespacesUrl);
            request.setConnectTimeout(ApolloConfigConstants.TIMEOUT);
            request.setReadTimeout(ApolloConfigConstants.TIMEOUT);
            HttpResponse<List<ApolloConfig>> response = httpUtil.doGet(request, new TypeToken<List<ApolloConfig>>() {}.getType());
            return response.getBody();
        } catch (Exception e) {
            throw new IllegalStateException(MessageFormat.format("获取Apollo信息异常，请求url=>{0}，请检查配置", namespacesUrl), e);
        }
    }

    /**
     * 获取应用信息，app_id
     * @return DefaultApplicationProvider
     */
    private DefaultApplicationProvider getApplicationProvider() {
        DefaultApplicationProvider defaultApplicationProvider = new DefaultApplicationProvider();
        defaultApplicationProvider.initialize();
        return defaultApplicationProvider;
    }

    /**
     * 获取Apollo AdminService服务数据
     * @param metaServiceUri  apollo配置服务地址
     * @return ServiceDTO
     */
    private ServiceDTO getAdminService(String metaServiceUri) {
        //组装admin service 地址 ，http://ip:port/services/admin
        String adminServiceUrl = MessageFormat.format(ApolloConfigConstants.ADMIN_SERVICE_URL_PATTERN, metaServiceUri);
        try {
            HttpUtil httpUtil = new HttpUtil();
            HttpRequest request = new HttpRequest(adminServiceUrl);
            request.setConnectTimeout(ApolloConfigConstants.TIMEOUT);
            request.setReadTimeout(ApolloConfigConstants.TIMEOUT);
            HttpResponse<List<ServiceDTO>> response = httpUtil.doGet(request, new TypeToken<List<ServiceDTO>>() {}.getType());
            List<ServiceDTO> adminServiceList = response.getBody();
            Assert.notEmpty(adminServiceList, MessageFormat.format("无Apollo AdminService服务实例，请检查服务，如无需使用Apollo，请在VM参数中配置-D{0}=false", ApolloConfigConstants.APOLLO_ENABLE));
            return adminServiceList.get(0);
        } catch (Exception e) {
            throw new IllegalStateException(MessageFormat.format("获取Apollo AdminService服务实例信息异常，请求url=>{0}，请检查配置，如无需使用Apollo，请在VM参数中配置-D{1}=false", adminServiceUrl, ApolloConfigConstants.APOLLO_ENABLE), e);
        }
    }

    private String getConfigServiceUri(DefaultApplicationProvider defaultApplicationProvider) {
        //获取配置的apollo地址，http://ip:port
        String configServiceUri = System.getProperty(ConfigConsts.APOLLO_META_KEY);
        if (StringUtils.isBlank(configServiceUri)) {
            configServiceUri = defaultApplicationProvider.getProperty(ConfigConsts.APOLLO_META_KEY, StringUtils.EMPTY);
        }
        Assert.hasText(configServiceUri, MessageFormat.format("Apollo ConfigService uri未配置，如无需使用Apollo，请在VM参数中配置{0}=false", ApolloConfigConstants.APOLLO_ENABLE));
        configServiceUri = configServiceUri.indexOf(",") > 0 ? configServiceUri.split(",")[0] : configServiceUri;
        return configServiceUri;
    }
}