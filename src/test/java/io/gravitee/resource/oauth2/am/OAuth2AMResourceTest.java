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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.resource.oauth2.am.configuration.OAuth2ResourceConfiguration;
import io.vertx.core.Vertx;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.MockitoAnnotations.initMocks;

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

    @InjectMocks
    private OAuth2AMResource resource;

    @Before
    public void init() {
        initMocks(this);
        Mockito.when(applicationContext.getBean(Vertx.class)).thenReturn(Vertx.vertx());
    }

    @Test
    public void shouldCallWithFormBody() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        stubFor(post(urlEqualTo("/domain/oauth/check_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"key\": \"value\"}")));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.introspect(accessToken, oAuth2Response -> lock.countDown());

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));

        verify(postRequestedFor(urlEqualTo("/domain/oauth/check_token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo("token="+accessToken)));
    }

    @Test
    public void shouldNotValidateAccessToken() throws Exception {
        String accessToken = "xxxx-xxxx-xxxx-xxxx";
        stubFor(post(urlEqualTo("/domain/oauth/check_token"))
                .willReturn(aResponse()
                        .withStatus(401)));

        final CountDownLatch lock = new CountDownLatch(1);

        Mockito.when(configuration.getSecurityDomain()).thenReturn("domain");
        Mockito.when(configuration.getServerURL()).thenReturn("http://localhost:" + wireMockRule.port());

        resource.doStart();

        resource.introspect(accessToken, oAuth2Response -> {
            Assert.assertFalse(oAuth2Response.isSuccess());
            lock.countDown();
        });

        Assert.assertEquals(true, lock.await(10000, TimeUnit.MILLISECONDS));
    }
}