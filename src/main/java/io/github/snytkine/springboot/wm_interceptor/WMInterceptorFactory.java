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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Spring {@code @Configuration} class that creates the {@link WMInterceptor} bean.
 *
 * <p>This factory is imported by {@link
 * io.github.snytkine.springboot.wm_interceptor.autoconfigure.WMInterceptorAutoConfiguration} and
 * only runs when the starter is enabled. You do not need to reference this class directly.
 */
@Configuration
public class WMInterceptorFactory {

  /**
   * Creates the WireMock interceptor bean and registers it with the Spring application context.
   *
   * <p>The bean is named {@code "restClientWiremockInterceptor"} and carries {@code @Order(50)},
   * which controls its position when multiple {@code ClientHttpRequestInterceptor} beans are
   * present. Note that Spring does not sort injected interceptor lists automatically — if ordering
   * matters in your application, sort the list explicitly using {@code
   * AnnotationAwareOrderComparator}.
   *
   * @param wireMockConfiguration the WireMock server settings built by {@link
   *     WireMockConfigurationFactory}
   * @param properties the user-supplied configuration properties
   * @return a fully initialised {@link WMInterceptor} ready to intercept HTTP requests
   */
  @Bean("restClientWiremockInterceptor")
  @Order(50)
  public WMInterceptor restClientWiremockInterceptor(
      WireMockConfiguration wireMockConfiguration, WireMockProperties properties) {
    return new WMInterceptor(wireMockConfiguration, properties);
  }
}
