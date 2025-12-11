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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.plugin.annotation.ConfigurationEvaluator;
import io.gravitee.plugin.configurations.http.HttpClientOptions;
import io.gravitee.plugin.configurations.http.HttpProxyOptions;
import io.gravitee.plugin.configurations.ssl.SslOptions;
import io.gravitee.resource.api.ResourceConfiguration;
import io.gravitee.secrets.api.annotation.Secret;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ConfigurationEvaluator
@Data
public class OAuth2ResourceConfiguration implements ResourceConfiguration {

    private String serverURL;

    private String securityDomain;

    @Secret
    private String clientId;

    @Secret
    private String clientSecret;

    private Version version = Version.V1_X;

    private String userClaim;

    @Setter(AccessLevel.NONE)
    private boolean useSystemProxy;

    @JsonProperty("http")
    private HttpClientOptions httpClientOptions = new HttpClientOptions();

    @JsonProperty("proxy")
    private HttpProxyOptions httpProxyOptions = new HttpProxyOptions();

    @JsonProperty("ssl")
    @Setter(AccessLevel.NONE)
    private SslOptions sslOptions;

    public void setSslOptions(SslOptions sslOptions) {
        // smooth migration: older versions of the plugin didn't have the sslOptions property,
        // but when the target was a secured schema (https), we enforced hostnameVerifier to false and trustAll to true.
        // This setter does not break the backward compatibility, and accept new sslOptions properly for new APIs and updates.
        if (sslOptions == null) {
            this.sslOptions = SslOptions.builder().hostnameVerifier(false).trustAll(true).build();
            return;
        }
        this.sslOptions = sslOptions;
    }

    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
        // smooth migration: older versions of the plugin didn't have the httpProxyOptions property,
        // so we simply set the httpProxyOptions.enabled and httpProxyOptions.setUseSystemProxy property
        // to avoid huge data migration.
        if (useSystemProxy) {
            this.httpProxyOptions.setEnabled(true);
            this.httpProxyOptions.setUseSystemProxy(true);
        }
    }

    public enum Version {
        V1_X,
        V2_X,
        V3_X,
    }
}
