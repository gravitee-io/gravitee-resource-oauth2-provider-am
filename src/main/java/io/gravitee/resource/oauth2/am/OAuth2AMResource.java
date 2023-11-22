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

import static io.gravitee.common.util.VertxProxyOptionsUtils.*;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.utils.UUID;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.utils.NodeUtils;
import io.gravitee.node.container.spring.SpringEnvironmentConfiguration;
import io.gravitee.resource.oauth2.am.configuration.OAuth2ResourceConfiguration;
import io.gravitee.resource.oauth2.api.OAuth2Resource;
import io.gravitee.resource.oauth2.api.OAuth2ResourceException;
import io.gravitee.resource.oauth2.api.OAuth2Response;
import io.gravitee.resource.oauth2.api.openid.UserInfoResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

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

    private static final String PATH_SEPARATOR = "/";
    private ApplicationContext applicationContext;

    private final Map<Thread, HttpClient> httpClients = new ConcurrentHashMap<>();

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

        int authorizationServerPort = introspectionUrl.getPort() != -1
            ? introspectionUrl.getPort()
            : (HTTPS_SCHEME.equals(introspectionUrl.getProtocol()) ? 443 : 80);

        // URI.getHost does not support '_' in the name, so we are using an intermediate URL to get the final host
        String authorizationServerHost = introspectionUrl.getHost();

        httpClientOptions =
            new HttpClientOptions()
                .setDefaultPort(authorizationServerPort)
                .setDefaultHost(authorizationServerHost)
                .setIdleTimeout(60)
                .setConnectTimeout(10000);

        if (configuration().isUseSystemProxy()) {
            try {
                Configuration nodeConfig = new SpringEnvironmentConfiguration(applicationContext.getEnvironment());
                setSystemProxy(httpClientOptions, nodeConfig);
            } catch (IllegalStateException e) {
                logger.warn(
                    "AMResource requires a system proxy to be defined to call [{}] but some configurations are missing or not well defined: {}",
                    configuration().getServerURL(),
                    e.getMessage()
                );
                logger.warn("Ignoring system proxy");
            }
        }

        // Use SSL connection if authorization schema is set to HTTPS
        if (HTTPS_SCHEME.equalsIgnoreCase(introspectionUrl.getProtocol())) {
            httpClientOptions.setSsl(true).setVerifyHost(false).setTrustAll(true);
        }

        introspectionEndpointAuthorization =
            AUTHORIZATION_HEADER_BASIC_SCHEME +
            Base64
                .getEncoder()
                .encodeToString(
                    (
                        configuration().getClientId() + AUTHORIZATION_HEADER_VALUE_BASE64_SEPARATOR + configuration().getClientSecret()
                    ).getBytes()
                );

        String path = (!introspectionUrl.getPath().isEmpty()) ? introspectionUrl.getPath() : PATH_SEPARATOR;
        if (!path.endsWith(PATH_SEPARATOR)) {
            path += PATH_SEPARATOR;
        }

        // Prepare userinfo and introspection endpoints
        if (configuration().getVersion() == OAuth2ResourceConfiguration.Version.V1_X) {
            introspectionEndpointURI = path + configuration().getSecurityDomain() + CHECK_TOKEN_ENDPOINT;

            userInfoEndpointURI = path + configuration().getSecurityDomain() + USERINFO_ENDPOINT;
        } else {
            introspectionEndpointURI = path + configuration().getSecurityDomain() + INTROSPECT_ENDPOINT_V2;

            userInfoEndpointURI = path + configuration().getSecurityDomain() + USERINFO_ENDPOINT_V2;
        }

        userAgent = NodeUtils.userAgent(applicationContext.getBean(Node.class));
        vertx = applicationContext.getBean(Vertx.class);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        httpClients
            .values()
            .forEach(httpClient -> {
                try {
                    httpClient.close();
                } catch (IllegalStateException ise) {
                    logger.warn(ise.getMessage());
                }
            });
    }

    @Override
    public void introspect(String accessToken, Handler<OAuth2Response> responseHandler) {
        HttpClient httpClient = httpClients.computeIfAbsent(Thread.currentThread(), context -> vertx.createHttpClient(httpClientOptions));

        logger.debug("Introspect access token by requesting {}", introspectionEndpointURI);

        final RequestOptions reqOptions = new RequestOptions()
            .setMethod(HttpMethod.POST)
            .setURI(introspectionEndpointURI)
            .putHeader(HttpHeaders.USER_AGENT, userAgent)
            .putHeader("X-Gravitee-Request-Id", UUID.toString(UUID.random()))
            .putHeader(HttpHeaders.AUTHORIZATION, introspectionEndpointAuthorization)
            .putHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
            .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
            .setTimeout(30000L);

        httpClient
            .request(reqOptions)
            .onFailure(
                new io.vertx.core.Handler<Throwable>() {
                    @Override
                    public void handle(Throwable event) {
                        logger.error("An error occurs while checking access token", event);
                        responseHandler.handle(new OAuth2Response(event));
                    }
                }
            )
            .onSuccess(
                new io.vertx.core.Handler<HttpClientRequest>() {
                    @Override
                    public void handle(HttpClientRequest request) {
                        request
                            .response(
                                new io.vertx.core.Handler<AsyncResult<HttpClientResponse>>() {
                                    @Override
                                    public void handle(AsyncResult<HttpClientResponse> asyncResponse) {
                                        if (asyncResponse.failed()) {
                                            logger.error("An error occurs while checking access token", asyncResponse.cause());
                                            responseHandler.handle(new OAuth2Response(asyncResponse.cause()));
                                        } else {
                                            final HttpClientResponse response = asyncResponse.result();
                                            logger.debug(
                                                "AM Introspection endpoint returns a response with a {} status code",
                                                response.statusCode()
                                            );
                                            response.bodyHandler(buffer -> {
                                                if (response.statusCode() == HttpStatusCode.OK_200) {
                                                    if (configuration().getVersion() == OAuth2ResourceConfiguration.Version.V1_X) {
                                                        responseHandler.handle(new OAuth2Response(true, buffer.toString()));
                                                    } else {
                                                        // Introspection Response from AM v2 always returns HTTP 200
                                                        // with an "active" boolean indicator of whether or not the presented token is currently active.
                                                        // retrieve active indicator
                                                        JsonObject jsonObject = buffer.toJsonObject();
                                                        boolean active = jsonObject.getBoolean(INTROSPECTION_ACTIVE_INDICATOR, false);
                                                        responseHandler.handle(
                                                            new OAuth2Response(
                                                                active,
                                                                (active) ? buffer.toString() : "{\"error\": \"Invalid Access Token\"}"
                                                            )
                                                        );
                                                    }
                                                } else {
                                                    logger.error(
                                                        "An error occurs while checking access token. Request ends with status {}: {}",
                                                        response.statusCode(),
                                                        buffer.toString()
                                                    );
                                                    responseHandler.handle(
                                                        new OAuth2Response(
                                                            new OAuth2ResourceException("An error occurs while checking access token")
                                                        )
                                                    );
                                                }
                                            });
                                        }
                                    }
                                }
                            )
                            .exceptionHandler(
                                new io.vertx.core.Handler<Throwable>() {
                                    @Override
                                    public void handle(Throwable event) {
                                        logger.error("An error occurs while checking access token", event);
                                        responseHandler.handle(new OAuth2Response(event));
                                    }
                                }
                            )
                            .end("token=" + accessToken);
                    }
                }
            );
    }

    @Override
    public void userInfo(String accessToken, Handler<UserInfoResponse> responseHandler) {
        HttpClient httpClient = httpClients.computeIfAbsent(Thread.currentThread(), context -> vertx.createHttpClient(httpClientOptions));

        logger.debug("Get userinfo from {}", userInfoEndpointURI);

        final RequestOptions reqOptions = new RequestOptions()
            .setMethod(HttpMethod.GET)
            .setURI(userInfoEndpointURI)
            .putHeader(HttpHeaders.USER_AGENT, userAgent)
            .putHeader("X-Gravitee-Request-Id", UUID.toString(UUID.random()))
            .putHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION_HEADER_BEARER_SCHEME + accessToken)
            .putHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);

        httpClient
            .request(reqOptions)
            .onFailure(
                new io.vertx.core.Handler<Throwable>() {
                    @Override
                    public void handle(Throwable event) {
                        logger.error("An error occurs while getting userinfo from access token", event);
                        responseHandler.handle(new UserInfoResponse(false, event.getMessage()));
                    }
                }
            )
            .onSuccess(
                new io.vertx.core.Handler<HttpClientRequest>() {
                    @Override
                    public void handle(HttpClientRequest request) {
                        request
                            .response(
                                new io.vertx.core.Handler<AsyncResult<HttpClientResponse>>() {
                                    @Override
                                    public void handle(AsyncResult<HttpClientResponse> asyncResponse) {
                                        if (asyncResponse.failed()) {
                                            logger.error("An error occurs while introspecting access token", asyncResponse.cause());
                                            responseHandler.handle(new UserInfoResponse(false, asyncResponse.cause().getMessage()));
                                        } else {
                                            final HttpClientResponse response = asyncResponse.result();
                                            response.bodyHandler(buffer -> {
                                                logger.debug(
                                                    "Userinfo endpoint returns a response with a {} status code",
                                                    response.statusCode()
                                                );

                                                if (response.statusCode() == HttpStatusCode.OK_200) {
                                                    responseHandler.handle(new UserInfoResponse(true, buffer.toString()));
                                                } else {
                                                    responseHandler.handle(new UserInfoResponse(false, buffer.toString()));
                                                }
                                            });
                                        }
                                    }
                                }
                            )
                            .exceptionHandler(
                                new io.vertx.core.Handler<Throwable>() {
                                    @Override
                                    public void handle(Throwable event) {
                                        logger.error("An error occurs while introspecting access token", event);
                                        responseHandler.handle(new UserInfoResponse(false, event.getMessage()));
                                    }
                                }
                            )
                            .end();
                    }
                }
            );
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
