package com.lyy.fengxiao.fengxiao.apollo.plugin;

/**
 * @author fengxiao
 * @date 2020/4/29 14:18
 */

public interface ApolloConfigConstants {

    int TIMEOUT = 3000;
    String APOLLO_ENABLE = "apollo.enable";
    String ADMIN_SERVICE_URL_PATTERN = "{0}/services/admin";
    String NAMESPACES_URL_PATTERN = "{0}apps/{1}/clusters/{2}/namespaces";
}
