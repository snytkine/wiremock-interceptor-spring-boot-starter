/*
 * Copyright 2025 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.springboot.wm_interceptor;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.snytkine.springboot.wm_interceptor.model.WireMockProperties;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Translates {@link WireMockProperties} into a {@link WireMockConfiguration} bean.
 *
 * <p>{@link WireMockConfiguration} is WireMock's own settings object. This factory reads whatever
 * the developer put in {@code application.yml} (via {@link WireMockProperties}) and applies each
 * non-null value to the configuration. Only values that were explicitly set by the developer are
 * applied; the rest use WireMock's built-in defaults.
 *
 * <p>The Faker extension ({@code org.wiremock.RandomExtension}) is always registered, so {@code {{
 * random '...' }}} template helpers are available in every stub mapping without any additional
 * configuration.
 */
@Configuration
@Slf4j
public class WireMockConfigurationFactory {

  private final WireMockProperties wireMockProperties;

  /**
   * Creates the factory with the properties bound from {@code application.yml}.
   *
   * @param wireMockProperties configuration values supplied by the developer
   */
  public WireMockConfigurationFactory(WireMockProperties wireMockProperties) {
    this.wireMockProperties = wireMockProperties;
  }

  /**
   * Builds and returns the {@link WireMockConfiguration} bean.
   *
   * <p>Each optional property is only applied when the developer explicitly set it; otherwise
   * WireMock's default is used. The Faker extension and {@code trustAllProxyTargets} are always
   * enabled regardless of configuration.
   *
   * @return a {@link WireMockConfiguration} ready to be passed to {@link WMInterceptor}
   */
  @Bean
  @Order(50)
  public WireMockConfiguration wireMockConfiguration() {
    WireMockConfiguration wireMockConfiguration = new WireMockConfiguration();

    Optional.ofNullable(wireMockProperties.getContainerThreads())
        .ifPresent(v -> wireMockConfiguration.containerThreads(v));
    Optional.ofNullable(wireMockProperties.getAsynchronousResponseEnabled())
        .ifPresent(v -> wireMockConfiguration.asynchronousResponseEnabled(v));
    Optional.ofNullable(wireMockProperties.getAsynchronousResponseThreads())
        .ifPresent(v -> wireMockConfiguration.asynchronousResponseThreads(v));
    Optional.ofNullable(wireMockProperties.getRootDirectory())
        .ifPresent(v -> wireMockConfiguration.usingFilesUnderDirectory(v));

    if (Boolean.TRUE.equals(wireMockProperties.getJournalDisabled())) {
      wireMockConfiguration.disableRequestJournal();
    }

    Optional.ofNullable(wireMockProperties.getMaxRequestJournalEntries())
        .ifPresent(v -> wireMockConfiguration.maxRequestJournalEntries(v));
    Optional.ofNullable(wireMockProperties.getGzipDisabled())
        .ifPresent(v -> wireMockConfiguration.gzipDisabled(v));
    Optional.ofNullable(wireMockProperties.getDisableOptimizeXmlFactories())
        .ifPresent(v -> wireMockConfiguration.disableOptimizeXmlFactoriesLoading(v));
    Optional.ofNullable(wireMockProperties.getStubCorsEnabled())
        .ifPresent(v -> wireMockConfiguration.stubCorsEnabled(v));
    Optional.ofNullable(wireMockProperties.getStubRequestLoggingDisabled())
        .ifPresent(v -> wireMockConfiguration.stubRequestLoggingDisabled(v));
    // templatingEnabled and proxyPassThrough are primitive booleans on properties;
    // call directly
    wireMockConfiguration.templatingEnabled(wireMockProperties.isTemplatingEnabled());
    Optional.ofNullable(wireMockProperties.getMappingsClassPath())
        .ifPresent(v -> wireMockConfiguration.usingFilesUnderClasspath(v));
    wireMockConfiguration.proxyPassThrough(wireMockProperties.isProxyPassThrough());

    log.trace("Registering Faker Extension org.wiremock.RandomExtension...");
    wireMockConfiguration.extensions(new String[] {"org.wiremock.RandomExtension"});
    wireMockConfiguration.trustAllProxyTargets(true);

    return wireMockConfiguration;
  }
}
