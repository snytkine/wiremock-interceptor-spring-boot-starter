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

import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.snytkine.springboot.wm_interceptor.model.WireMockProperties;
import org.junit.jupiter.api.Test;

class WireMockConfigurationFactoryTest {

  @Test
  void createsConfigurationWithDefaults() {
    WireMockProperties properties = new WireMockProperties();
    WireMockConfigurationFactory factory = new WireMockConfigurationFactory(properties);

    WireMockConfiguration cfg = factory.wireMockConfiguration();
    assertNotNull(cfg);
  }

  @Test
  void createsConfigurationWithCustomValues() {
    WireMockProperties properties = new WireMockProperties();
    properties.setContainerThreads(5);
    properties.setAsynchronousResponseEnabled(true);
    properties.setAsynchronousResponseThreads(3);
    properties.setRootDirectory("/tmp/wiremock");
    properties.setJournalDisabled(true);
    properties.setMaxRequestJournalEntries(200);
    properties.setGzipDisabled(true);
    properties.setDisableOptimizeXmlFactories(true);
    properties.setStubCorsEnabled(true);
    properties.setStubRequestLoggingDisabled(true);
    properties.setTemplatingEnabled(true);
    properties.setMappingsClassPath("mappings");
    properties.setProxyPassThrough(true);

    WireMockConfigurationFactory factory = new WireMockConfigurationFactory(properties);
    WireMockConfiguration cfg = factory.wireMockConfiguration();
    assertNotNull(cfg);
  }
}
