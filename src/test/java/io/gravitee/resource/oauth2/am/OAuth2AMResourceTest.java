/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.resource.oauth2.am;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.el.TemplateEngine;
import io.gravitee.node.api.Node;
import io.gravitee.plugin.configurations.http.HttpClientOptions;
import io.gravitee.plugin.configurations.http.HttpProxyOptions;
import io.gravitee.plugin.configurations.ssl.SslOptions;
import io.gravitee.resource.api.AbstractConfigurableResource;
import io.gravitee.resource.oauth2.am.configuration.OAuth2ResourceConfiguration;
import io.gravitee.resource.oauth2.api.OAuth2ResourceMetadata;
import io.vertx.rxjava3.core.Vertx;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith({ MockitoExtension.class, WireMockExtension.class })
public class OAuth2AMResourceTest {

    private static TemplateEngine templateEngine;

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Mock
    private ApplicationContext applicationContext;

    private OAuth2ResourceConfiguration configuration;

    @Mock
    private Node node;

    private OAuth2AMResource resource;

    @BeforeAll
    static void beforeAll() {
        templateEngine = TemplateEngine.templateEngine();
    }

    @BeforeEach
    public void init() throws Exception {
        resource = new OAuth2AMResource();
        resource.setDeploymentContext(new TestDeploymentContext(templateEngine));
        resource.setApplicationContext(applicationContext);

        lenient().when(applicationContext.getBean(Vertx.class)).thenReturn(Vertx.vertx());
        lenient().when(applicationContext.getBean(Node.class)).thenReturn(node);

        configuration = new OAuth2ResourceConfiguration();
        configuration.setSecurityDomain("domain");
        configuration.setServerURL("http://localhost:" + wiremock.getPort());
        configuration.setHttpClientOptions(new HttpClientOptions());
        configuration.setHttpProxyOptions(new HttpProxyOptions());
        configuration.setSslOptions(new SslOptions());

        Field configurationField = AbstractConfigurableResource.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(resource, configuration);
    }

    @Test
    public void shouldCallWithFormBody() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        wiremock.stubFor(
            post(urlEqualTo("/domain/oauth/check_token")).willReturn(aResponse().withStatus(200).withBody("{\"key\": \"value\"}"))
        );

        final CountDownLatch lock = new CountDownLatch(1);

        resource.doStart();
        resource.introspect(accessToken, oAuth2Response -> lock.countDown());

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();

        wiremock.verify(
            postRequestedFor(urlEqualTo("/domain/oauth/check_token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo("token=" + accessToken))
        );
    }

    @Test
    public void shouldCallWithFormBody_v2() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        wiremock.stubFor(
            post(urlEqualTo("/domain/oauth/introspect")).willReturn(aResponse().withStatus(200).withBody("{\"key\": \"value\"}"))
        );

        final CountDownLatch lock = new CountDownLatch(1);

        configuration.setVersion(OAuth2ResourceConfiguration.Version.V2_X);

        resource.doStart();

        resource.introspect(accessToken, oAuth2Response -> lock.countDown());

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();

        wiremock.verify(
            postRequestedFor(urlEqualTo("/domain/oauth/introspect"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo("token=" + accessToken))
        );
    }

    @Test
    public void shouldNotValidateAccessToken() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        wiremock.stubFor(post(urlEqualTo("/domain/oauth/check_token")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        resource.doStart();

        resource.introspect(
            accessToken,
            oAuth2Response -> {
                assertThat(oAuth2Response.isSuccess()).isFalse();
                assertThat(oAuth2Response.getPayload()).isEqualTo("An error occurs while checking access token");
                lock.countDown();
            }
        );

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void shouldNotValidateAccessToken_v2() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        wiremock.stubFor(
            post(urlEqualTo("/domain/oauth/introspect")).willReturn(aResponse().withStatus(200).withBody("{\"active\": false}"))
        );

        final CountDownLatch lock = new CountDownLatch(1);

        configuration.setVersion(OAuth2ResourceConfiguration.Version.V2_X);

        resource.doStart();

        resource.introspect(
            accessToken,
            oAuth2Response -> {
                assertThat(oAuth2Response.isSuccess()).isFalse();
                lock.countDown();
            }
        );

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void shouldNotValidateAccessToken_v2_not_200() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        wiremock.stubFor(post(urlEqualTo("/domain/oauth/introspect")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        configuration.setVersion(OAuth2ResourceConfiguration.Version.V2_X);

        resource.doStart();

        resource.introspect(
            accessToken,
            oAuth2Response -> {
                assertThat(oAuth2Response.isSuccess()).isFalse();
                assertThat(oAuth2Response.getPayload()).isEqualTo("An error occurs while checking access token");
                lock.countDown();
            }
        );

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void shouldGetUserInfo() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/domain/userinfo"))
                .willReturn(
                    aResponse().withStatus(200).withBody("{\"sub\": \"248289761001\", \"name\": \"Jane Doe\", \"given_name\": \"Jane\"}")
                )
        );

        final CountDownLatch lock = new CountDownLatch(1);

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                assertThat(userInfoResponse.isSuccess()).isTrue();
                lock.countDown();
            }
        );

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void shouldGetUserInfo_v2() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/domain/oidc/userinfo"))
                .willReturn(
                    aResponse().withStatus(200).withBody("{\"sub\": \"248289761001\", \"name\": \"Jane Doe\", \"given_name\": \"Jane\"}")
                )
        );

        final CountDownLatch lock = new CountDownLatch(1);

        configuration.setVersion(OAuth2ResourceConfiguration.Version.V2_X);

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                assertThat(userInfoResponse.isSuccess()).isTrue();
                lock.countDown();
            }
        );

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void shouldNotGetUserInfo() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/domain/userinfo")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                assertThat(userInfoResponse.isSuccess()).isFalse();
                assertThat(userInfoResponse.getPayload()).isEqualTo("An error occurs while getting userinfo from access token");
                lock.countDown();
            }
        );

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void shouldAppendMissingTrailingSlah() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/test/domain/userinfo")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                assertThat(userInfoResponse.isSuccess()).isFalse();
                assertThat(userInfoResponse.getPayload()).isEqualTo("An error occurs while getting userinfo from access token");
                lock.countDown();
            }
        );

        assertThat(lock.await(10000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void getProtectedResourceMetadata_serverUrl_without_ending_slash_securityDomain_without_starting_and_trailing_slash()
        throws NoSuchFieldException, IllegalAccessException {
        testGetProtectedResourceMetadata("https://am.gateway.dev", "test");
    }

    @Test
    public void getProtectedResourceMetadata_serverUrl_with_ending_slash_securityDomain_without_starting_and_trailing_slash()
        throws NoSuchFieldException, IllegalAccessException {
        testGetProtectedResourceMetadata("https://am.gateway.dev/", "test");
    }

    @Test
    public void getProtectedResourceMetadata_serverUrl_with_ending_slash_securityDomain_with_starting_and_no_trailing_slash()
        throws NoSuchFieldException, IllegalAccessException {
        testGetProtectedResourceMetadata("https://am.gateway.dev/", "/test");
    }

    @Test
    public void getProtectedResourceMetadata_serverUrl_with_ending_slash_securityDomain_with_starting_and_trailing_slash()
        throws NoSuchFieldException, IllegalAccessException {
        testGetProtectedResourceMetadata("https://am.gateway.dev/", "/test/");
    }

    @Test
    public void getProtectedResourceMetadata_serverUrl_without_ending_slash_securityDomain_with_starting_and_trailing_slash()
        throws NoSuchFieldException, IllegalAccessException {
        testGetProtectedResourceMetadata("https://am.gateway.dev", "/test/");
    }

    private void testGetProtectedResourceMetadata(String serverUrl, String securityDomain)
        throws NoSuchFieldException, IllegalAccessException {
        OAuth2AMResource resource = new OAuth2AMResource();
        OAuth2ResourceConfiguration configuration = new OAuth2ResourceConfiguration();
        configuration.setServerURL(serverUrl);
        configuration.setSecurityDomain(securityDomain);
        Field configurationField = AbstractConfigurableResource.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(resource, configuration);
        OAuth2ResourceMetadata resourceMetadata = resource.getProtectedResourceMetadata("https://backend.com");
        assertThat(resourceMetadata.protectedResourceUri()).isEqualTo("https://backend.com");
        assertThat(resourceMetadata.authorizationServers().get(0)).isEqualTo("https://am.gateway.dev/test/oidc");
        assertThat(resourceMetadata.authorizationServers()).hasSize(1);
        assertThat(resourceMetadata.scopesSupported()).isNull();
    }
}
