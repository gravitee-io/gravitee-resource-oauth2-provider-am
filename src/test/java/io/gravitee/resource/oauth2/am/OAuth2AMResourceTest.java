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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.MockitoAnnotations.initMocks;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.node.api.Node;
import io.gravitee.resource.api.AbstractConfigurableResource;
import io.gravitee.resource.oauth2.am.configuration.OAuth2ResourceConfiguration;
import io.vertx.core.Vertx;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OAuth2AMResourceTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private OAuth2ResourceConfiguration configuration;

    @Mock
    private Node node;

    private OAuth2AMResource resource;

    @Before
    public void init() throws Exception {
        resource = new OAuth2AMResource();
        resource.setApplicationContext(applicationContext);

        Field configurationField = AbstractConfigurableResource.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(resource, configuration);

        Mockito.when(applicationContext.getBean(Vertx.class)).thenReturn(Vertx.vertx());
        Mockito.when(applicationContext.getBean(Node.class)).thenReturn(node);
        Mockito.when(configuration.getVersion()).thenReturn(new OAuth2ResourceConfiguration().getVersion());
        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());
    }

    @Test
    public void shouldCallWithFormBody() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        stubFor(post(urlEqualTo("/domain/oauth/check_token")).willReturn(aResponse().withStatus(200).withBody("{\"key\": \"value\"}")));

        final CountDownLatch lock = new CountDownLatch(1);

        resource.doStart();
        resource.introspect(accessToken, oAuth2Response -> lock.countDown());

        Assert.assertTrue(lock.await(10000, TimeUnit.MILLISECONDS));

        verify(
            postRequestedFor(urlEqualTo("/domain/oauth/check_token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo("token=" + accessToken))
        );
    }

    @Test
    public void shouldCallWithFormBody_v2() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        stubFor(post(urlEqualTo("/domain/oauth/introspect")).willReturn(aResponse().withStatus(200).withBody("{\"key\": \"value\"}")));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getVersion()).thenReturn(OAuth2ResourceConfiguration.Version.V2_X);
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.introspect(accessToken, oAuth2Response -> lock.countDown());

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));

        verify(
            postRequestedFor(urlEqualTo("/domain/oauth/introspect"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo("token=" + accessToken))
        );
    }

    @Test
    public void shouldNotValidateAccessToken() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        stubFor(post(urlEqualTo("/domain/oauth/check_token")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.introspect(
            accessToken,
            oAuth2Response -> {
                Assert.assertFalse(oAuth2Response.isSuccess());
                Assert.assertEquals("An error occurs while checking access token", oAuth2Response.getPayload());
                lock.countDown();
            }
        );

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldNotValidateAccessToken_v2() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        stubFor(post(urlEqualTo("/domain/oauth/introspect")).willReturn(aResponse().withStatus(200).withBody("{\"active\": false}")));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getVersion()).thenReturn(OAuth2ResourceConfiguration.Version.V2_X);
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.introspect(
            accessToken,
            oAuth2Response -> {
                Assert.assertFalse(oAuth2Response.isSuccess());
                lock.countDown();
            }
        );

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldNotValidateAccessToken_v2_not_200() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        stubFor(post(urlEqualTo("/domain/oauth/introspect")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getVersion()).thenReturn(OAuth2ResourceConfiguration.Version.V2_X);
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.introspect(
            accessToken,
            oAuth2Response -> {
                Assert.assertFalse(oAuth2Response.isSuccess());
                Assert.assertEquals("An error occurs while checking access token", oAuth2Response.getPayload());
                lock.countDown();
            }
        );

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldGetUserInfo() throws Exception {
        stubFor(
            get(urlEqualTo("/domain/userinfo"))
                .willReturn(
                    aResponse().withStatus(200).withBody("{\"sub\": \"248289761001\", \"name\": \"Jane Doe\", \"given_name\": \"Jane\"}")
                )
        );

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                Assert.assertTrue(userInfoResponse.isSuccess());
                lock.countDown();
            }
        );

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldGetUserInfo_v2() throws Exception {
        stubFor(
            get(urlEqualTo("/domain/oidc/userinfo"))
                .willReturn(
                    aResponse().withStatus(200).withBody("{\"sub\": \"248289761001\", \"name\": \"Jane Doe\", \"given_name\": \"Jane\"}")
                )
        );

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getVersion()).thenReturn(OAuth2ResourceConfiguration.Version.V2_X);
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                Assert.assertTrue(userInfoResponse.isSuccess());
                lock.countDown();
            }
        );

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldNotGetUserInfo() throws Exception {
        stubFor(get(urlEqualTo("/domain/userinfo")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                Assert.assertFalse(userInfoResponse.isSuccess());
                Assert.assertEquals("An error occurs while getting userinfo from access token", userInfoResponse.getPayload());
                lock.countDown();
            }
        );

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldAppendMissingTrailingSlah() throws Exception {
        stubFor(get(urlEqualTo("/test/domain/userinfo")).willReturn(aResponse().withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port() + "/test");

        resource.doStart();

        resource.userInfo(
            "xxxx-xxxx-xxxx-xxxx",
            userInfoResponse -> {
                Assert.assertFalse(userInfoResponse.isSuccess());
                Assert.assertEquals("An error occurs while getting userinfo from access token", userInfoResponse.getPayload());
                lock.countDown();
            }
        );

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }
}
