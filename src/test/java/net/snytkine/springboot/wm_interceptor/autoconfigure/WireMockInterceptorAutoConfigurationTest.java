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
package net.snytkine.springboot.wm_interceptor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import net.snytkine.springboot.wm_interceptor.WMInterceptor;
import net.snytkine.springboot.wm_interceptor.WMInterceptorFactory;
import net.snytkine.springboot.wm_interceptor.WireMockConfigurationFactory;
import net.snytkine.springboot.wm_interceptor.model.WireMockProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WireMockInterceptorAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  net.snytkine.springboot.wm_interceptor.autoconfigure
                      .WMInterceptorAutoConfiguration.class));

  @Test
  void whenPropertyEnabled_thenBeansCreated() {
    runner
        .withPropertyValues("net.snytkine.rest-client-wiremock-interceptor.enabled=true")
        .run(
            (context) -> {
              assertThat(context).hasSingleBean(WireMockProperties.class);
              assertThat(context).hasSingleBean(WireMockConfigurationFactory.class);
              assertThat(context).hasSingleBean(WMInterceptorFactory.class);
              assertThat(context).hasSingleBean(WMInterceptor.class);
            });
  }

  @Test
  void whenPropertyMissing_thenBeansNotCreated() {
    runner.run((context) -> assertThat(context).doesNotHaveBean(WMInterceptor.class));
  }
}
