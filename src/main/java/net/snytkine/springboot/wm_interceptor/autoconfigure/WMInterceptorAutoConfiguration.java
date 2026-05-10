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

import net.snytkine.springboot.wm_interceptor.WMInterceptorFactory;
import net.snytkine.springboot.wm_interceptor.WireMockConfigurationFactory;
import net.snytkine.springboot.wm_interceptor.model.WireMockProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration that activates the WireMock request interceptor.
 *
 * <p>This class is picked up automatically by Spring Boot's auto-configuration mechanism (via the
 * entry in {@code META-INF/spring/...AutoConfiguration.imports}). It does nothing on its own — it
 * simply imports the two factory classes that create the necessary beans.
 *
 * <p>The entire configuration is conditional: all beans are created only when the property {@code
 * net.snytkine.rest-client-wiremock-interceptor.enabled=true} is present. If the property is absent
 * or false, no beans are registered and the application is unaffected.
 */
@AutoConfiguration
@EnableConfigurationProperties(WireMockProperties.class)
@ConditionalOnProperty(
    prefix = "net.snytkine.rest-client-wiremock-interceptor",
    name = "enabled",
    havingValue = "true")
@Import({WireMockConfigurationFactory.class, WMInterceptorFactory.class})
public class WMInterceptorAutoConfiguration {}
