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
package io.gravitee.resource.oauth2.am.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.el.TemplateEngine;
import io.gravitee.el.spel.context.SecuredResolver;
import io.gravitee.resource.api.ResourceConfiguration;
import io.gravitee.resource.oauth2.am.OAuth2AMResource;
import io.gravitee.resource.oauth2.am.TestDeploymentContext;
import io.gravitee.secrets.api.el.DelegatingEvaluatedSecretsMethods;
import io.gravitee.secrets.api.el.EvaluatedSecretsMethods;
import io.gravitee.secrets.api.el.FieldKind;
import io.gravitee.secrets.api.el.SecretFieldAccessControl;
import io.vertx.rxjava3.core.Vertx;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class OAuth2ResourceConfigurationTest {

    private static TemplateEngine templateEngine;

    @Mock
    private ApplicationContext applicationContext;

    private List<SecretFieldAccessControl> recordedSecretFieldAccessControls = new ArrayList<>();

    @BeforeAll
    static void init() {
        SecuredResolver.initialize(null);
        templateEngine = TemplateEngine.templateEngine();
    }

    @BeforeEach
    void setUp() {
        recordedSecretFieldAccessControls.clear();
        EvaluatedSecretsMethods delegate = new EvaluatedSecretsMethods() {
            @Override
            public String fromGrant(String secretValue, SecretFieldAccessControl secretFieldAccessControl) {
                recordedSecretFieldAccessControls.add(secretFieldAccessControl);
                return secretValue;
            }

            @Override
            public String fromGrant(String contextId, String secretKey, SecretFieldAccessControl secretFieldAccessControl) {
                return fromGrant(contextId, secretFieldAccessControl);
            }

            @Override
            public String fromEL(String contextId, String uriOrName, SecretFieldAccessControl secretFieldAccessControl) {
                return fromGrant(contextId, secretFieldAccessControl);
            }
        };
        templateEngine.getTemplateContext().setVariable("secrets", new DelegatingEvaluatedSecretsMethods(delegate));
        templateEngine.getTemplateContext().setVariable("host", "acme.com");
        templateEngine.getTemplateContext().setVariable("masterId", "r2d2");
        lenient().when(applicationContext.getBean(Vertx.class)).thenReturn(Vertx.vertx());
    }

    @Test
    public void testConfiguration_checkDefaultVersion() throws IOException {
        OAuth2ResourceConfiguration configuration = load("/io/gravitee/resource/oauth2/am/configuration/configuration1.json");

        assertThat(configuration.getVersion()).isEqualTo(OAuth2ResourceConfiguration.Version.V1_X);
    }

    @Test
    public void testConfiguration_checkVersion() throws IOException {
        OAuth2ResourceConfiguration configuration = load("/io/gravitee/resource/oauth2/am/configuration/configuration2.json");

        assertThat(configuration.getVersion()).isEqualTo(OAuth2ResourceConfiguration.Version.V2_X);
    }

    @Test
    void should_eval_config() throws Exception {
        OAuth2ResourceConfiguration config = new OAuth2ResourceConfiguration();
        config.setServerURL("http://localhost:8080/auth");
        config.setClientId(asSecretEL("that is an ID"));
        config.setClientSecret(asSecretEL("that is a secret"));
        config.getHttpProxyOptions().setPassword(asSecretEL("that is a password"));
        OAuth2AMResource resource = underTest(config);
        resource.start();

        assertThat(resource.configuration().getClientId()).isEqualTo("that is an ID");
        assertThat(resource.configuration().getClientSecret()).isEqualTo("that is a secret");
        assertThat(resource.configuration().getHttpProxyOptions().getPassword()).isEqualTo("that is a password");

        assertThat(recordedSecretFieldAccessControls)
            .containsExactlyInAnyOrder(
                new SecretFieldAccessControl(true, FieldKind.GENERIC, "clientSecret"),
                new SecretFieldAccessControl(true, FieldKind.GENERIC, "clientId"),
                new SecretFieldAccessControl(true, FieldKind.PASSWORD, "httpProxyOptions.password")
            );
    }

    @Test
    void should_not_be_able_to_resolve_secret_on_non_sensitive_field() throws Exception {
        OAuth2ResourceConfiguration config = new OAuth2ResourceConfiguration();
        config.setServerURL("http://localhost:8080/auth");
        config.setUserClaim(asSecretEL("user claim"));
        OAuth2AMResource oAuth2GenericResource = underTest(config);
        oAuth2GenericResource.start();
        assertThat(recordedSecretFieldAccessControls).containsExactlyInAnyOrder(new SecretFieldAccessControl(false, null, null));
    }

    private OAuth2ResourceConfiguration load(String resource) throws IOException {
        URL jsonFile = this.getClass().getResource(resource);
        return objectMapper().readValue(jsonFile, OAuth2ResourceConfiguration.class);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private static String asSecretEL(String password) {
        return "{#secrets.fromGrant('%s', #%s)}".formatted(password, SecretFieldAccessControl.EL_VARIABLE);
    }

    OAuth2AMResource underTest(OAuth2ResourceConfiguration config) throws IllegalAccessException {
        OAuth2AMResource resource = new OAuth2AMResource();
        Optional<Field> configuration = Stream
            .of(resource.getClass().getSuperclass().getSuperclass().getDeclaredFields())
            .filter(field -> field.getName().equals("configuration") && field.getType().equals(ResourceConfiguration.class))
            .findFirst();
        if (configuration.isPresent()) {
            Field field = configuration.get();
            field.setAccessible(true);
            field.set(resource, config);
        } else {
            fail("configuration field not found");
        }
        resource.setDeploymentContext(new TestDeploymentContext(templateEngine));
        resource.setApplicationContext(applicationContext);
        return resource;
    }
}
