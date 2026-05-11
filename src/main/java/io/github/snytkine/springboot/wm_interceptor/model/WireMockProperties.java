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
package io.github.snytkine.springboot.wm_interceptor.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the WireMock RestClient interceptor.
 *
 * <p>All properties are read from {@code application.yml} (or {@code application.properties}) under
 * the prefix {@code io.github.snytkine.rest-client-wiremock-interceptor}. For example:
 *
 * <pre>{@code
 * io:
 *   github:
 *     snytkine:
 *       rest-client-wiremock-interceptor:
 *         enabled: true
 *         mappings-class-path: mocks
 *         templating-enabled: true
 * }</pre>
 *
 * <p>Lombok's {@code @Data} generates getters, setters, {@code equals}, {@code hashCode}, and
 * {@code toString} automatically, so you will not find those methods in this source file.
 */
@ConfigurationProperties(prefix = "io.github.snytkine.rest-client-wiremock-interceptor")
@Component
@Data
public class WireMockProperties {

  /**
   * Master switch for the interceptor. The entire starter is inactive when this is {@code false}.
   * Must be set to {@code true} in your configuration for any mocking to occur.
   */
  private boolean enabled = false;

  /**
   * Number of threads WireMock uses internally to process matched requests. Increase this if you
   * are making many concurrent requests during a test and see contention.
   */
  private Integer containerThreads = 1;

  /**
   * When {@code true}, WireMock delivers stub responses on a separate thread pool rather than the
   * calling thread. Useful for simulating slow or asynchronous services.
   */
  private Boolean asynchronousResponseEnabled = false;

  /**
   * Number of threads in the async response thread pool. Only relevant when {@link
   * #asynchronousResponseEnabled} is {@code true}.
   */
  private Integer asynchronousResponseThreads;

  /**
   * Absolute filesystem path where WireMock looks for stub mapping files and response body files.
   * Use this when your stubs live outside the classpath (e.g. a directory on disk). If you keep
   * stubs inside {@code src/main/resources}, use {@link #mappingsClassPath} instead.
   */
  private String rootDirectory;

  /**
   * When {@code true}, WireMock does not record a history of received requests (the "journal").
   * Disabling the journal reduces memory usage in scenarios with a very high request volume.
   */
  private Boolean journalDisabled;

  /**
   * Maximum number of past requests to keep in the journal. Once the limit is reached, the oldest
   * entry is dropped when a new request arrives. Has no effect when {@link #journalDisabled} is
   * {@code true}.
   */
  private Integer maxRequestJournalEntries;

  /**
   * When {@code true}, disables automatic gzip compression of WireMock responses. Set this if a
   * client does not handle gzip-encoded responses correctly.
   */
  private Boolean gzipDisabled;

  /**
   * When {@code true}, disables WireMock's optimised XML factory loading. Set this only if you
   * encounter XML parsing conflicts with other libraries on the classpath.
   */
  private Boolean disableOptimizeXmlFactories = false;

  /**
   * When {@code true}, WireMock adds CORS response headers ({@code Access-Control-Allow-Origin},
   * etc.) to stub responses. Useful when the interceptor is used in a context where browser-style
   * CORS checks apply.
   */
  private Boolean stubCorsEnabled = false;

  /**
   * When {@code true}, WireMock does not write a log line for each request that matches a stub. Set
   * this to reduce noise in application logs during testing.
   */
  private Boolean stubRequestLoggingDisabled;

  /**
   * Maximum number of compiled Handlebars/response templates to keep in WireMock's template cache.
   * Increase this if you have many unique templates and see repeated compilation overhead.
   */
  private Long maxTemplateCacheEntries;

  /**
   * When {@code true}, response templating is applied to every stub response automatically, without
   * needing the {@code "response-template"} transformer listed in each mapping file. Requires
   * {@link #templatingEnabled} to also be {@code true}.
   */
  private Boolean globalTemplating = false;

  /**
   * Master switch for WireMock's response templating engine. Must be {@code true} to use {@code
   * {{...}}} placeholders (e.g. {@code {{request.path.[0]}}}, {@code {{now}}}) in stub response
   * bodies. Without this, template expressions are returned as plain text.
   */
  private boolean templatingEnabled = false;

  /**
   * Classpath-relative root directory that contains your stub mapping files. The directory must
   * contain a {@code mappings/} subdirectory where your {@code .json} mapping files are placed.
   * Optionally, a {@code __files/} subdirectory can hold separate response body files referenced by
   * {@code bodyFileName} in a mapping. Example: setting this to {@code "mocks"} means WireMock
   * loads stubs from {@code src/main/resources/mocks/mappings/}.
   */
  private String mappingsClassPath;

  /**
   * When {@code true}, HTTP requests that do not match any stub are forwarded to their real
   * destination server. When {@code false} (the default), unmatched requests receive an error
   * response from WireMock instead of reaching the real server.
   */
  private boolean proxyPassThrough;

  /**
   * Optional HTTP response header name added to every mocked response. When set, any caller that
   * receives a response can inspect this header to determine whether the response came from
   * WireMock or from the real server. Example: {@code "X-Mock-Response"}.
   */
  private String mockResponseHeader;

  /**
   * Value to use for the {@link #mockResponseHeader}. If the header name is configured but this
   * value is {@code null}, the interceptor falls back to {@code "mock-middleware"}.
   */
  private String mockResponseHeaderValue;
}
