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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2ResourceConfigurationTest {

    @Test
    public void testConfiguration_checkDefaultVersion() throws IOException {
        OAuth2ResourceConfiguration configuration = load("/io/gravitee/resource/oauth2/am/configuration/configuration1.json");

        Assert.assertEquals(OAuth2ResourceConfiguration.Version.V1_X, configuration.getVersion());
    }

    @Test
    public void testConfiguration_checkVersion() throws IOException {
        OAuth2ResourceConfiguration configuration = load("/io/gravitee/resource/oauth2/am/configuration/configuration2.json");

        Assert.assertEquals(OAuth2ResourceConfiguration.Version.V2_X, configuration.getVersion());
    }

    private OAuth2ResourceConfiguration load(String resource) throws IOException {
        URL jsonFile = this.getClass().getResource(resource);
        return objectMapper().readValue(jsonFile, OAuth2ResourceConfiguration.class);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
