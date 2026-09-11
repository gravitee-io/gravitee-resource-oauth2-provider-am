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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.json.validation.InvalidJsonException;
import io.gravitee.json.validation.JsonSchemaValidator;
import io.gravitee.json.validation.JsonSchemaValidatorImpl;
import io.gravitee.resource.oauth2.am.configuration.OAuth2ResourceConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Configurations written before the schema was split into variants do not carry the
 * variant discriminator. They must keep validating and deserializing unchanged.
 *
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SchemaFormBackwardCompatibilityTest {

    private static final JsonSchemaValidator VALIDATOR = new JsonSchemaValidatorImpl();

    /** Same leniency as the gateway, see ResourceConfigurationFactoryImpl. */
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String LEGACY_CONFIGURATION = """
        {
          "serverURL": "https://am.example.com",
          "version": "V3_X",
          "securityDomain": "a-domain",
          "clientId": "a-client",
          "clientSecret": "a-secret",
          "userClaim": "sub"
        }
        """;

    private static final String USERINFO_CONFIGURATION = """
        {
          "mode": "USERINFO",
          "serverURL": "https://am.example.com",
          "version": "V3_X",
          "securityDomain": "a-domain",
          "userClaim": "sub"
        }
        """;

    private static String schema;

    @BeforeAll
    static void readPackagedSchema() throws IOException {
        // the packaged schema, with shared definitions resolved
        try (InputStream in = SchemaFormBackwardCompatibilityTest.class.getResourceAsStream("/schemas/schema-form.json")) {
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void a_configuration_without_the_discriminator_is_still_accepted() {
        assertThatCode(() -> VALIDATOR.validate(schema, LEGACY_CONFIGURATION)).doesNotThrowAnyException();
    }

    @Test
    void a_userinfo_configuration_is_accepted_without_client_credentials() {
        assertThatCode(() -> VALIDATOR.validate(schema, USERINFO_CONFIGURATION)).doesNotThrowAnyException();
    }

    @Test
    void an_introspection_configuration_without_client_id_is_rejected() {
        String json = """
            {
              "mode": "INTROSPECTION",
              "serverURL": "https://am.example.com",
              "version": "V3_X",
              "securityDomain": "a-domain"
            }
            """;
        assertThatThrownBy(() -> VALIDATOR.validate(schema, json)).isInstanceOf(InvalidJsonException.class);
    }

    @Test
    void a_userinfo_configuration_without_a_security_domain_is_rejected() {
        String json = """
            {
              "mode": "USERINFO",
              "serverURL": "https://am.example.com",
              "version": "V3_X"
            }
            """;
        assertThatThrownBy(() -> VALIDATOR.validate(schema, json)).isInstanceOf(InvalidJsonException.class);
    }

    @Test
    void a_configuration_without_the_discriminator_still_deserializes() throws IOException {
        OAuth2ResourceConfiguration configuration = MAPPER.readValue(LEGACY_CONFIGURATION, OAuth2ResourceConfiguration.class);

        assertThat(configuration.getServerURL()).isEqualTo("https://am.example.com");
        assertThat(configuration.getSecurityDomain()).isEqualTo("a-domain");
        assertThat(configuration.getClientId()).isEqualTo("a-client");
    }

    @Test
    void the_discriminator_is_ignored_when_deserializing() throws IOException {
        OAuth2ResourceConfiguration configuration = MAPPER.readValue(USERINFO_CONFIGURATION, OAuth2ResourceConfiguration.class);

        assertThat(configuration.getServerURL()).isEqualTo("https://am.example.com");
        assertThat(configuration.getSecurityDomain()).isEqualTo("a-domain");
    }
}
