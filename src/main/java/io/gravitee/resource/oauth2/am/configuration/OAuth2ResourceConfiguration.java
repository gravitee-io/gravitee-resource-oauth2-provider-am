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
import io.gravitee.plugin.configurations.http.HttpClientOptions;
import io.gravitee.plugin.configurations.http.HttpProxyOptions;
import io.gravitee.plugin.configurations.ssl.SslOptions;
import io.gravitee.resource.api.ResourceConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2ResourceConfiguration implements ResourceConfiguration {

    private String serverURL;

    private String securityDomain;

    private String clientId;

    private String clientSecret;

    private Version version = Version.V1_X;

    private String userClaim;

    private boolean useSystemProxy;

    @JsonProperty("http")
    private HttpClientOptions httpClientOptions = new HttpClientOptions();

    @JsonProperty("proxy")
    private HttpProxyOptions httpProxyOptions = new HttpProxyOptions();

    @JsonProperty("ssl")
    private SslOptions sslOptions = new SslOptions();

    public String getServerURL() {
        return serverURL;
    }

    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }

    public String getSecurityDomain() {
        return securityDomain;
    }

    public void setSecurityDomain(String securityDomain) {
        this.securityDomain = securityDomain;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public String getUserClaim() {
        return userClaim;
    }

    public void setUserClaim(String userClaim) {
        this.userClaim = userClaim;
    }

    public boolean isUseSystemProxy() {
        return useSystemProxy;
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

    public HttpClientOptions getHttpClientOptions() {
        return httpClientOptions;
    }

    public void setHttpClientOptions(HttpClientOptions httpClientOptions) {
        this.httpClientOptions = httpClientOptions;
    }

    public HttpProxyOptions getHttpProxyOptions() {
        return httpProxyOptions;
    }

    public void setHttpProxyOptions(HttpProxyOptions httpProxyOptions) {
        this.httpProxyOptions = httpProxyOptions;
    }

    public SslOptions getSslOptions() {
        return sslOptions;
    }

    public void setSslOptions(SslOptions sslOptions) {
        this.sslOptions = sslOptions;
    }

    public enum Version {
        V1_X,
        V2_X,
        V3_X,
    }
}
