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
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.resource.oauth2.am.configuration.OAuth2ResourceConfiguration;
import io.gravitee.resource.oauth2.api.OAuth2Resource;
import io.gravitee.resource.oauth2.api.OAuth2Response;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.net.URI;
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
    private static final char AUTHORIZATION_HEADER_VALUE_BASE64_SEPARATOR = ':';
    private static final String CHECK_TOKEN_ENDPOINT = "/oauth/check_token";

    private ApplicationContext applicationContext;

    private final Map<Context, HttpClient> httpClients = new HashMap<>();

    private HttpClientOptions httpClientOptions;

    private Vertx vertx;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Starting an OAuth2 resource using Gravitee.io Access Management server at {}", configuration().getServerURL());

        URI introspectionUri = URI.create(configuration().getServerURL());

        int authorizationServerPort = introspectionUri.getPort() != -1 ? introspectionUri.getPort() :
                (HTTPS_SCHEME.equals(introspectionUri.getScheme()) ? 443 : 80);
        String authorizationServerHost = introspectionUri.getHost();

        httpClientOptions = new HttpClientOptions()
                .setDefaultPort(authorizationServerPort)
                .setDefaultHost(authorizationServerHost);

        // Use SSL connection if authorization schema is set to HTTPS
        if (HTTPS_SCHEME.equalsIgnoreCase(introspectionUri.getScheme())) {
            httpClientOptions
                    .setSsl(true)
                    .setVerifyHost(false)
                    .setTrustAll(true);
        }

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

        OAuth2ResourceConfiguration configuration = configuration();

        String introspectionEndpointURI = configuration.getServerURL() +
                '/' +
                configuration.getSecurityDomain() +
                CHECK_TOKEN_ENDPOINT;

        logger.debug("Introspect access token by requesting {} [{}]", introspectionEndpointURI);

        HttpClientRequest request = httpClient.post(introspectionEndpointURI);

        String authorizationValue = AUTHORIZATION_HEADER_BASIC_SCHEME +
                    Base64.getEncoder().encodeToString(
                            (configuration.getClientId() +
                                    AUTHORIZATION_HEADER_VALUE_BASE64_SEPARATOR +
                                    configuration.getClientSecret()).getBytes());
        request.headers().add(HttpHeaders.AUTHORIZATION, authorizationValue);

        // Set `Accept` header to ask for application/json content
        request.headers().add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        request.headers().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);

        request.handler(response -> response.bodyHandler(buffer -> {
            logger.debug("AM Introspection endpoint returns a response with a {} status code", response.statusCode());
            if (response.statusCode() == HttpStatusCode.OK_200) {
                responseHandler.handle(new OAuth2Response(true, buffer.toString()));
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
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
