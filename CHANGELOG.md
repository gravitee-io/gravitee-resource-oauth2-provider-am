# [3.2.0-alpha.1](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/3.1.0...3.2.0-alpha.1) (2025-12-03)


### Features

* add the OAuth2AMResource.getProtectedResourceMetadata() method ([b366184](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/b3661845c367604ee31af70c5b0efb165e951906))

# [3.1.0](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/3.0.0...3.1.0) (2025-11-19)


### Features

* allow to configure max concurrent connections ([9b5e2b6](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/9b5e2b6058326ef1a720e14412eade1756f0f699))

# [3.0.0](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/2.1.0...3.0.0) (2024-12-16)


### Bug Fixes

* lint project after parent update ([cbbaaba](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/cbbaaba44cee3a62805445995fc024c8acb3383c))
* use VertxProxyOptionsUtils from gravitee node ([4f49a3d](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/4f49a3d38d9707cba13c403b23f260e33badecd4))


### BREAKING CHANGES

* require APIM 4.4.x

# [2.1.0](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/2.0.2...2.1.0) (2024-07-11)


### Features

* rework schema-form to use new GioJsonSchema Ui component ([ea3a932](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/ea3a93244adbc8cead964bf9e9be2269e76d5f38))

## [2.0.2](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/2.0.1...2.0.2) (2024-03-21)


### Bug Fixes

* **deps:** update dependency io.gravitee.gateway:gravitee-gateway-api to v1.47.1 ([f58a2cc](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/f58a2cc6e015d69b65370ecc8ef746bee67a3246))

## [2.0.1](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/2.0.0...2.0.1) (2023-11-23)


### Bug Fixes

* use a throwable in the Oauth2Response on technical error for introspection ([c13a525](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/c13a52541dc4185a017c7cda39a53d1616a5329a))
* use a throwable in the UserInfoResponse on technical error for userInfo ([9814a5f](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/9814a5f6720fdf179f9098d11a5a1762fdc652ff))

# [2.0.0](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/1.14.2...2.0.0) (2022-05-24)


### Code Refactoring

* use common vertx proxy options factory ([4dd0175](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/4dd01756f92c044e6e3d76a403fb0b0d7c78dc2d))


### BREAKING CHANGES

* this version requires APIM in version 3.18 and upper

## [1.14.2](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/compare/1.14.1...1.14.2) (2022-04-05)


### Bug Fixes

* change default version of AM server to v3 ([87706c0](https://github.com/gravitee-io/gravitee-resource-oauth2-provider-am/commit/87706c01a5064ded6a30fe1a1999464bc5e9f958))
