/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.resource.oauth2.am;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.utils.UUID;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.utils.NodeUtils;
import io.gravitee.resource.oauth2.am.configuration.OAuth2ResourceConfiguration;
import io.gravitee.resource.oauth2.api.OAuth2Resource;
import io.gravitee.resource.oauth2.api.OAuth2Response;
import io.gravitee.resource.oauth2.api.openid.UserInfoResponse;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2AMResource extends OAuth2Resource<OAuth2ResourceConfiguration> implements ApplicationContextAware {

    private final Logger logger = LoggerFactory.getLogger(OAuth2AMResource.class);

    private static final String HTTPS_SCHEME = "https";

    private static final String AUTHORIZATION_HEADER_BASIC_SCHEME = "Basic ";
    private static final String AUTHORIZATION_HEADER_BEARER_SCHEME = "Bearer ";
    private static final char AUTHORIZATION_HEADER_VALUE_BASE64_SEPARATOR = ':';

    private static final String CHECK_TOKEN_ENDPOINT = "/oauth/check_token";
    private static final String INTROSPECT_ENDPOINT_V2 = "/oauth/introspect";

    private static final String USERINFO_ENDPOINT = "/userinfo";
    private static final String USERINFO_ENDPOINT_V2 = "/oidc/userinfo";

    private static final String INTROSPECTION_ACTIVE_INDICATOR = "active";

    private ApplicationContext applicationContext;

    private final Map<Context, HttpClient> httpClients = new HashMap<>();

    private HttpClientOptions httpClientOptions;

    private Vertx vertx;
    private String userAgent;

    private String introspectionEndpointURI;
    private String introspectionEndpointAuthorization;
    private String userInfoEndpointURI;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Starting an OAuth2 resource using Gravitee.io Access Management server at {}", configuration().getServerURL());

        URL introspectionUrl = new URL(configuration().getServerURL());

        int authorizationServerPort = introspectionUrl.getPort() != -1 ? introspectionUrl.getPort() :
                (HTTPS_SCHEME.equals(introspectionUrl.getProtocol()) ? 443 : 80);

        // URI.getHost does not support '_' in the name, so we are using an intermediate URL to get the final host
        String authorizationServerHost = introspectionUrl.getHost();

        httpClientOptions = new HttpClientOptions()
                .setDefaultPort(authorizationServerPort)
                .setDefaultHost(authorizationServerHost)
                .setIdleTimeout(60)
                .setConnectTimeout(10000);

        // Use SSL connection if authorization schema is set to HTTPS
        if (HTTPS_SCHEME.equalsIgnoreCase(introspectionUrl.getProtocol())) {
            httpClientOptions
                    .setSsl(true)
                    .setVerifyHost(false)
                    .setTrustAll(true);
        }

        introspectionEndpointAuthorization = AUTHORIZATION_HEADER_BASIC_SCHEME +
                Base64.getEncoder().encodeToString(
                        (configuration().getClientId() + AUTHORIZATION_HEADER_VALUE_BASE64_SEPARATOR +
                                configuration().getClientSecret()).getBytes());

        String path = (! introspectionUrl.getPath().isEmpty()) ? introspectionUrl.getPath() : "/";

        // Prepare userinfo and introspection endpoints
        if (configuration().getVersion() == OAuth2ResourceConfiguration.Version.V1_X) {
            introspectionEndpointURI = path + configuration().getSecurityDomain() +
                    CHECK_TOKEN_ENDPOINT;

            userInfoEndpointURI = path + configuration().getSecurityDomain() +
                    USERINFO_ENDPOINT;
        } else {
            introspectionEndpointURI = path + configuration().getSecurityDomain() +
                    INTROSPECT_ENDPOINT_V2;

            userInfoEndpointURI = path + configuration().getSecurityDomain() +
                    USERINFO_ENDPOINT_V2;
        }

        userAgent = NodeUtils.userAgent(applicationContext.getBean(Node.class));
        vertx = applicationContext.getBean(Vertx.class);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        httpClients.values().forEach(httpClient -> {
            try {
                httpClient.close();
            } catch (IllegalStateException ise) {
                logger.warn(ise.getMessage());
            }
        });
    }

    @Override
    public void introspect(String accessToken, Handler<OAuth2Response> responseHandler) {
        HttpClient httpClient = httpClients.computeIfAbsent(
                Vertx.currentContext(), context -> vertx.createHttpClient(httpClientOptions));

        logger.debug("Introspect access token by requesting {}", introspectionEndpointURI);

        HttpClientRequest request = httpClient.post(introspectionEndpointURI);
        request.setTimeout(30000L);
        request.headers().add(HttpHeaders.USER_AGENT, userAgent);
        request.headers().add("X-Gravitee-Request-Id", UUID.toString(UUID.random()));
        request.headers().add(HttpHeaders.AUTHORIZATION, introspectionEndpointAuthorization);
        request.headers().add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        request.headers().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);

        request.handler(response -> response.bodyHandler(buffer -> {
            logger.debug("AM Introspection endpoint returns a response with a {} status code", response.statusCode());
            if (response.statusCode() == HttpStatusCode.OK_200) {
                if (configuration().getVersion() == OAuth2ResourceConfiguration.Version.V1_X) {
                    responseHandler.handle(new OAuth2Response(true, buffer.toString()));
                } else {
                    // Introspection Response from AM v2 always returns HTTP 200
                    // with an "active" boolean indicator of whether or not the presented token is currently active.
                    // retrieve active indicator
                    JsonObject jsonObject = buffer.toJsonObject();
                    boolean active = jsonObject.getBoolean(INTROSPECTION_ACTIVE_INDICATOR, false);
                    responseHandler.handle(new OAuth2Response(active, (active) ? buffer.toString() : "{\"error\": \"Invalid Access Token\"}"));
                }
            } else {
                responseHandler.handle(new OAuth2Response(false, buffer.toString()));
            }
        }));

        request.exceptionHandler(event -> {
            logger.error("An error occurs while checking access_token", event);
            responseHandler.handle(new OAuth2Response(false, event.getMessage()));
        });

        request.end("token=" + accessToken);
    }

    @Override
    public void userInfo(String accessToken, Handler<UserInfoResponse> responseHandler) {
        HttpClient httpClient = httpClients.computeIfAbsent(
                Vertx.currentContext(), context -> vertx.createHttpClient(httpClientOptions));

        logger.debug("Get userinfo from {}", userInfoEndpointURI);

        HttpClientRequest request = httpClient.get(userInfoEndpointURI);

        request.headers().add(HttpHeaders.USER_AGENT, userAgent);
        request.headers().add("X-Gravitee-Request-Id", UUID.toString(UUID.random()));
        request.headers().add(HttpHeaders.AUTHORIZATION, AUTHORIZATION_HEADER_BEARER_SCHEME + accessToken);
        request.headers().add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);

        request.handler(response -> response.bodyHandler(buffer -> {
            logger.debug("Userinfo endpoint returns a response with a {} status code", response.statusCode());

            if (response.statusCode() == HttpStatusCode.OK_200) {
                responseHandler.handle(new UserInfoResponse(true, buffer.toString()));
            } else {
                responseHandler.handle(new UserInfoResponse(false, buffer.toString()));
            }
        }));

        request.exceptionHandler(event -> {
            logger.error("An error occurs while getting userinfo from access_token", event);
            responseHandler.handle(new UserInfoResponse(false, event.getMessage()));
        });

        request.end();
    }

    @Override
    public String getUserClaim() {
        return configuration().getUserClaim();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
