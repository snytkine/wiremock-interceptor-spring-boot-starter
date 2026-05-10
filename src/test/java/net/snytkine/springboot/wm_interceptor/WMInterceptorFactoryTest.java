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
package net.snytkine.springboot.wm_interceptor;

import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import net.snytkine.springboot.wm_interceptor.model.WireMockProperties;
import org.junit.jupiter.api.Test;

class WMInterceptorFactoryTest {

  @Test
  void createsInterceptor() {
    WMInterceptorFactory factory = new WMInterceptorFactory();

    WireMockConfiguration cfg = new WireMockConfiguration();
    WireMockProperties props = new WireMockProperties();

    WMInterceptor interceptor = factory.restClientWiremockInterceptor(cfg, props);
    assertNotNull(interceptor);
  }

  @Test
  void wiresPropertiesIntoInterceptor() throws Exception {
    WMInterceptorFactory factory = new WMInterceptorFactory();

    WireMockConfiguration cfg = new WireMockConfiguration();
    WireMockProperties props = new WireMockProperties();
    props.setMockResponseHeader("X-FACTORY");

    WMInterceptor interceptor = factory.restClientWiremockInterceptor(cfg, props);
    assertNotNull(interceptor);

    // verify the private 'properties' field references the same object
    var field = WMInterceptor.class.getDeclaredField("properties");
    field.setAccessible(true);
    Object contained = field.get(interceptor);
    assertSame(props, contained);

    // verify DirectCallHttpServer was created
    var serverField = WMInterceptor.class.getDeclaredField("directCallHttpServer");
    serverField.setAccessible(true);
    Object server = serverField.get(interceptor);
    assertNotNull(server);
  }
}
