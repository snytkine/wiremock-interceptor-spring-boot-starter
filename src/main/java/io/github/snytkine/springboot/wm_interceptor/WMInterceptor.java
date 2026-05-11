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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.FormParameter;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.github.snytkine.springboot.wm_interceptor.model.WireMockProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Spring {@link org.springframework.http.client.ClientHttpRequestInterceptor} that silently routes
 * matching outgoing HTTP requests to WireMock stubs instead of the real network.
 *
 * <p>Every time a {@code RestClient} (or {@code RestTemplate}) makes an HTTP call, this interceptor
 * runs first. It converts the Spring request into WireMock's internal format, asks WireMock whether
 * any configured stub matches, and then either:
 *
 * <ul>
 *   <li>returns the stub's mocked response immediately (no network call made), or
 *   <li>passes the request through to the real server unchanged if no stub matched.
 * </ul>
 *
 * <p>WireMock matching is done entirely in-process using {@link DirectCallHttpServer} — there is no
 * actual HTTP server listening on a port. This makes the interceptor fast and suitable for use in
 * production as well as in tests.
 *
 * <p>If {@link WireMockProperties#getMockResponseHeader()} is configured, a header is added to
 * every mocked response so the caller can tell it came from WireMock rather than the real server.
 *
 * @see WireMockProperties
 * @see WireMockConfigurationFactory
 */
@Slf4j
public class WMInterceptor implements ClientHttpRequestInterceptor {
  /** WireMock's in-process request matcher — no network port, no HTTP server thread. */
  private final DirectCallHttpServer directCallHttpServer;

  /** User-supplied configuration controlling mock headers, templating, stub location, etc. */
  private final WireMockProperties properties;

  /**
   * Creates the interceptor and initialises the in-process WireMock stub matcher.
   *
   * <p>A {@link DirectCallHttpServerFactory} is used so that WireMock loads and indexes the stub
   * mappings without binding to any network port. After this constructor returns, the interceptor
   * is ready to match requests immediately.
   *
   * @param wireMockConfiguration WireMock server settings (stub location, templating, etc.),
   *     typically produced by {@link WireMockConfigurationFactory}
   * @param properties user configuration such as which header to add to mocked responses
   */
  public WMInterceptor(WireMockConfiguration wireMockConfiguration, WireMockProperties properties) {
    this.properties = properties;
    DirectCallHttpServerFactory wireMockServer = new DirectCallHttpServerFactory();
    wireMockConfiguration.httpServerFactory(wireMockServer);
    WireMockServer wm = new WireMockServer(wireMockConfiguration);
    wm.start(); // no-op, not required
    this.directCallHttpServer = wireMockServer.getHttpServer();
  }

  @Override
  public @NonNull ClientHttpResponse intercept(
      @NonNull HttpRequest request,
      @NonNull byte[] body,
      @NonNull ClientHttpRequestExecution execution)
      throws IOException {
    log.trace("Entered intercept");
    Request wiremockRequest = new SpringHttpRequestAdapter(request, body);

    com.github.tomakehurst.wiremock.http.Response wiremockResponse =
        directCallHttpServer.stubRequest(wiremockRequest);

    if (wiremockResponse.wasConfigured()) {
      log.trace("Returning mock response");
      var ret = new WiremockClientHttpResponse(wiremockResponse);
      String mockKey = properties.getMockResponseHeader();
      String mockHeaderValue =
          java.util.Objects.requireNonNullElse(
              properties.getMockResponseHeaderValue(), "mock-middleware");
      if (mockKey != null) {
        log.trace("Adding mock header {}={}", mockKey, mockHeaderValue);
        ret.setHeader(mockKey, mockHeaderValue);
      }
      return ret;
    }

    log.trace("Returning real response");
    return execution.execute(request, body);
  }

  /**
   * Bridges Spring's {@link HttpRequest} to WireMock's {@link Request} interface.
   *
   * <p>WireMock's stub-matching engine only knows about its own {@link Request} type. This adapter
   * wraps a Spring {@link HttpRequest} so it can be handed directly to WireMock for matching,
   * without copying all request data upfront. Method, URI, headers, query parameters, and body are
   * translated on demand as WireMock's matcher inspects them.
   *
   * <p>A few Spring-to-WireMock mapping decisions worth noting:
   *
   * <ul>
   *   <li>{@code getPort()} returns {@code 80} for HTTP and {@code 443} for HTTPS when the URI does
   *       not include an explicit port number (Spring returns {@code -1} in that case).
   *   <li>{@code getClientIp()} always returns {@code "0.0.0.0"} because Spring's {@link
   *       HttpRequest} does not expose the local client address.
   *   <li>Multipart detection is based solely on the {@code Content-Type} header prefix ({@code
   *       multipart/form-data}); individual parts are not parsed and {@link #getParts()} always
   *       returns an empty collection.
   * </ul>
   */
  private static class SpringHttpRequestAdapter implements Request {
    private final HttpRequest springRequest;
    private final byte[] body;
    private final Map<String, QueryParameter> queryParameters;

    private Map<String, QueryParameter> parseQueryParameters() {
      Map<String, QueryParameter> params = new HashMap<>();
      UriComponentsBuilder.fromUri(springRequest.getURI())
          .build()
          .getQueryParams()
          .forEach((key, values) -> params.put(key, new QueryParameter(key, values)));
      return Collections.unmodifiableMap(params);
    }

    /**
     * Wraps an outgoing Spring HTTP request so it can be matched against WireMock stubs.
     *
     * @param springRequest the outgoing request as seen by the interceptor
     * @param body the raw request body bytes (may be empty for GET/DELETE requests)
     */
    public SpringHttpRequestAdapter(HttpRequest springRequest, byte[] body) {
      this.springRequest = springRequest;
      this.body = body;
      this.queryParameters = parseQueryParameters();
    }

    @Override
    public String getUrl() {
      String url = springRequest.getURI().getPath();
      if (springRequest.getURI().getQuery() != null) {
        url += "?" + springRequest.getURI().getQuery();
      }
      return url;
    }

    @Override
    public String getAbsoluteUrl() {
      return springRequest.getURI().toString();
    }

    @Override
    public RequestMethod getMethod() {
      return RequestMethod.fromString(springRequest.getMethod().name());
    }

    @Override
    public String getScheme() {
      return springRequest.getURI().getScheme();
    }

    @Override
    public String getHost() {
      return springRequest.getURI().getHost();
    }

    @Override
    public int getPort() {
      int port = springRequest.getURI().getPort();
      if (port == -1) {
        if ("http".equals(getScheme())) {
          return 80;
        } else if ("https".equals(getScheme())) {
          return 443;
        }
      }
      return port;
    }

    @Override
    public String getClientIp() {
      return "0.0.0.0"; // Not available in Spring's HttpRequest
    }

    @Override
    @SuppressWarnings("null")
    public @NonNull String getHeader(String key) {
      return java.util.Objects.requireNonNullElse(springRequest.getHeaders().getFirst(key), "");
    }

    @Override
    public ContentTypeHeader contentTypeHeader() {
      MediaType contentType = springRequest.getHeaders().getContentType();
      if (contentType == null) {
        return ContentTypeHeader.absent();
      }
      return new ContentTypeHeader(contentType.toString());
    }

    @Override
    public HttpHeaders getHeaders() {
      List<HttpHeader> httpHeaders = new ArrayList<>();
      springRequest
          .getHeaders()
          .forEach((key, values) -> httpHeaders.add(new HttpHeader(key, values)));
      return new HttpHeaders(httpHeaders);
    }

    @Override
    public boolean containsHeader(String key) {
      return springRequest.getHeaders().containsKey(key);
    }

    @Override
    public Set<String> getAllHeaderKeys() {
      Set<String> res = springRequest.getHeaders().keySet();
      return res;
    }

    @Override
    public QueryParameter queryParameter(String key) {
      return queryParameters.get(key);
    }

    @Override
    public byte[] getBody() {
      return body;
    }

    @Override
    public String getBodyAsString() {
      return new String(body, StandardCharsets.UTF_8);
    }

    @Override
    public String getBodyAsBase64() {
      return Base64.getEncoder().encodeToString(body);
    }

    @Override
    public boolean isMultipart() {
      MediaType contentType = springRequest.getHeaders().getContentType();
      return contentType != null && contentType.toString().startsWith("multipart/form-data");
    }

    @Override
    public Collection<Request.Part> getParts() {
      return Collections.emptyList();
    }

    @Override
    public Request.Part getPart(String name) {
      return null;
    }

    @Override
    public boolean isBrowserProxyRequest() {
      return false;
    }

    @Override
    public Optional<Request> getOriginalRequest() {
      return Optional.empty();
    }

    @Override
    public FormParameter formParameter(String arg0) {
      return null;
    }

    @Override
    public Map<String, FormParameter> formParameters() {
      return null;
    }

    @Override
    public Map<String, Cookie> getCookies() {
      return new HashMap<>();
    }

    @Override
    public String getProtocol() {
      return "https";
    }

    @Override
    @SuppressWarnings("null")
    public @NonNull HttpHeader header(String arg0) {
      String myHeader =
          java.util.Objects.requireNonNullElse(springRequest.getHeaders().getFirst(arg0), "");
      if (!myHeader.isEmpty()) {
        return new HttpHeader(arg0, List.of(myHeader));
      }
      return new HttpHeader(arg0, Collections.emptyList());
    }
  }

  /**
   * Bridges a WireMock stub {@link com.github.tomakehurst.wiremock.http.Response} to Spring's
   * {@link ClientHttpResponse} interface.
   *
   * <p>When a stub matches, WireMock returns its own {@code Response} object. Spring's HTTP client
   * stack expects a {@link ClientHttpResponse}. This class wraps the WireMock response so it can be
   * returned directly to the caller without any Spring code knowing WireMock was involved.
   *
   * <p>All headers from the WireMock stub are copied into a mutable Spring {@link
   * org.springframework.http.HttpHeaders} map during construction. The {@link #setHeader(String,
   * String)} method allows the interceptor to inject additional headers afterwards (e.g. the
   * mock-identification header). A {@code null} body in the WireMock response is normalised to an
   * empty byte array so callers never receive a {@code null} stream.
   */
  private static class WiremockClientHttpResponse implements ClientHttpResponse {
    private final com.github.tomakehurst.wiremock.http.Response wiremockResponse;

    @NonNull private org.springframework.http.HttpHeaders ownHeaders;

    public void setHeader(@NonNull String key, String value) {
      this.ownHeaders.set(key, value);
    }

    public WiremockClientHttpResponse(
        com.github.tomakehurst.wiremock.http.Response wiremockResponse) {
      this.wiremockResponse = wiremockResponse;
      this.ownHeaders = new org.springframework.http.HttpHeaders();
      if (wiremockResponse.getHeaders() != null) {
        for (HttpHeader header : wiremockResponse.getHeaders().all()) {
          String key = header.key();
          java.util.List<String> values = header.values();
          if (key != null && values != null) {
            ownHeaders.addAll(key, values);
          }
        }
      }
    }

    @Override
    public @NonNull HttpStatusCode getStatusCode() throws IOException {
      return HttpStatusCode.valueOf(wiremockResponse.getStatus());
    }

    @Override
    @SuppressWarnings("null")
    public @NonNull String getStatusText() throws IOException {
      return java.util.Objects.requireNonNullElse(wiremockResponse.getStatusMessage(), "");
    }

    @Override
    public void close() {
      // no-op
    }

    @Override
    public @NonNull InputStream getBody() throws IOException {
      byte[] b = wiremockResponse.getBody();
      if (b == null) {
        b = new byte[0];
      }
      return new ByteArrayInputStream(b);
    }

    @Override
    public @NonNull org.springframework.http.HttpHeaders getHeaders() {
      return ownHeaders;
    }
  }
}
