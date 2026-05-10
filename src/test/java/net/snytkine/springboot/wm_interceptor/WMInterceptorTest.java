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
import static org.mockito.Mockito.*;

import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.snytkine.springboot.wm_interceptor.model.WireMockProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class WMInterceptorTest {

  private static HttpRequest simpleRequest(String uri) {
    return new HttpRequest() {
      @Override
      public HttpMethod getMethod() {
        return HttpMethod.GET;
      }

      @Override
      public URI getURI() {
        return URI.create(uri);
      }

      @Override
      public org.springframework.http.HttpHeaders getHeaders() {
        return new org.springframework.http.HttpHeaders();
      }
    };
  }

  @Test
  void interceptReturnsMockResponseWithMockHeader() throws Exception {
    WireMockProperties props = new WireMockProperties();
    props.setMockResponseHeader("X-MOCK");
    props.setMockResponseHeaderValue("mock-middleware");

    WMInterceptor interceptor =
        new WMInterceptor(new com.github.tomakehurst.wiremock.core.WireMockConfiguration(), props);

    // mock the DirectCallHttpServer to return a configured response
    DirectCallHttpServer mockServer = mock(DirectCallHttpServer.class);
    Response mockResponse = mock(Response.class);
    when(mockResponse.wasConfigured()).thenReturn(true);
    when(mockResponse.getStatus()).thenReturn(200);
    when(mockResponse.getBody()).thenReturn("hello".getBytes(StandardCharsets.UTF_8));
    when(mockResponse.getStatusMessage()).thenReturn("OK");
    when(mockResponse.getHeaders()).thenReturn(null);
    when(mockServer.stubRequest(any())).thenReturn(mockResponse);

    // inject the mock server into the interceptor
    java.lang.reflect.Field f = WMInterceptor.class.getDeclaredField("directCallHttpServer");
    f.setAccessible(true);
    f.set(interceptor, mockServer);

    // execution that should NOT be called
    ClientHttpRequestExecution exec =
        (request, body) -> {
          fail("Execution should not be called when mock response is configured");
          return null;
        };

    ClientHttpResponse resp =
        interceptor.intercept(simpleRequest("http://localhost/test"), new byte[0], exec);
    assertEquals(200, resp.getStatusCode().value());
    String body = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("hello", body);
    assertEquals("mock-middleware", resp.getHeaders().getFirst("X-MOCK"));
  }

  @Test
  void springHttpRequestAdapterBehaviors() throws Exception {
    HttpRequest req =
        new HttpRequest() {
          @Override
          public HttpMethod getMethod() {
            return HttpMethod.POST;
          }

          @Override
          public URI getURI() {
            return URI.create("https://example.com:443/path/name?x=1&y=2");
          }

          @Override
          public org.springframework.http.HttpHeaders getHeaders() {
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.set("Content-Type", "multipart/form-data; boundary=abc");
            h.set("X-Test", "value");
            return h;
          }
        };

    Class<?> adapterCls =
        Class.forName(
            "net.snytkine.springboot.wm_interceptor.WMInterceptor$SpringHttpRequestAdapter");
    java.lang.reflect.Constructor<?> ctor =
        adapterCls.getDeclaredConstructor(HttpRequest.class, byte[].class);
    ctor.setAccessible(true);
    com.github.tomakehurst.wiremock.http.Request adapter =
        (com.github.tomakehurst.wiremock.http.Request)
            ctor.newInstance(req, "body".getBytes(StandardCharsets.UTF_8));

    assertEquals("/path/name?x=1&y=2", adapter.getUrl());
    assertEquals("https://example.com:443/path/name?x=1&y=2", adapter.getAbsoluteUrl());
    assertEquals(com.github.tomakehurst.wiremock.http.RequestMethod.POST, adapter.getMethod());
    assertEquals("https", adapter.getScheme());
    assertEquals("example.com", adapter.getHost());
    assertEquals(443, adapter.getPort());
    assertEquals("0.0.0.0", adapter.getClientIp());
    assertEquals("value", adapter.getHeader("X-Test"));
    assertTrue(adapter.isMultipart());
    assertEquals("body", adapter.getBodyAsString());
    assertNotNull(adapter.getBodyAsBase64());
  }

  @Test
  void wiremockClientHttpResponseCopiesHeaders() throws Exception {
    Response mockResponse = mock(Response.class);
    when(mockResponse.getStatus()).thenReturn(201);
    when(mockResponse.getStatusMessage()).thenReturn("Created");
    when(mockResponse.getBody()).thenReturn("payload".getBytes(StandardCharsets.UTF_8));
    HttpHeader hh = new HttpHeader("H", java.util.List.of("v1", "v2"));
    HttpHeaders hhs = new HttpHeaders(java.util.List.of(hh));
    when(mockResponse.getHeaders()).thenReturn(hhs);

    Class<?> respCls =
        Class.forName(
            "net.snytkine.springboot.wm_interceptor.WMInterceptor$WiremockClientHttpResponse");
    java.lang.reflect.Constructor<?> ctor = respCls.getDeclaredConstructor(Response.class);
    ctor.setAccessible(true);
    org.springframework.http.client.ClientHttpResponse resp =
        (org.springframework.http.client.ClientHttpResponse) ctor.newInstance(mockResponse);

    assertEquals(201, resp.getStatusCode().value());
    assertEquals("Created", resp.getStatusText());
    assertEquals("v1", resp.getHeaders().getFirst("H"));
    String body = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("payload", body);
  }

  @Test
  void contentTypeHeaderPresentAndAbsent() throws Exception {
    HttpRequest noCt =
        new HttpRequest() {
          @Override
          public HttpMethod getMethod() {
            return HttpMethod.GET;
          }

          @Override
          public URI getURI() {
            return URI.create("http://example.com/path");
          }

          @Override
          public org.springframework.http.HttpHeaders getHeaders() {
            return new org.springframework.http.HttpHeaders();
          }
        };

    Class<?> adapterCls =
        Class.forName(
            "net.snytkine.springboot.wm_interceptor.WMInterceptor$SpringHttpRequestAdapter");
    java.lang.reflect.Constructor<?> ctor =
        adapterCls.getDeclaredConstructor(HttpRequest.class, byte[].class);
    ctor.setAccessible(true);
    com.github.tomakehurst.wiremock.http.Request adapterNoCt =
        (com.github.tomakehurst.wiremock.http.Request) ctor.newInstance(noCt, new byte[0]);

    com.github.tomakehurst.wiremock.http.ContentTypeHeader cta = adapterNoCt.contentTypeHeader();
    assertNotNull(cta);
    assertTrue(
        cta.toString().isBlank()
            || cta == com.github.tomakehurst.wiremock.http.ContentTypeHeader.absent());

    HttpRequest withCt =
        new HttpRequest() {
          @Override
          public HttpMethod getMethod() {
            return HttpMethod.POST;
          }

          @Override
          public URI getURI() {
            return URI.create("http://example.com/path");
          }

          @Override
          public org.springframework.http.HttpHeaders getHeaders() {
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return h;
          }
        };

    com.github.tomakehurst.wiremock.http.Request adapterWithCt =
        (com.github.tomakehurst.wiremock.http.Request) ctor.newInstance(withCt, new byte[0]);
    com.github.tomakehurst.wiremock.http.ContentTypeHeader ctb = adapterWithCt.contentTypeHeader();
    assertNotNull(ctb);
    assertTrue(ctb.toString().contains("application/json"));
  }

  @Test
  void queryParamsAndHeaderMapping() throws Exception {
    HttpRequest req =
        new HttpRequest() {
          @Override
          public HttpMethod getMethod() {
            return HttpMethod.GET;
          }

          @Override
          public URI getURI() {
            return URI.create("http://example.com/path?x=1&x=2&y=single");
          }

          @Override
          public org.springframework.http.HttpHeaders getHeaders() {
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.addAll("H", java.util.List.of("a", "b"));
            h.set("C", "c");
            return h;
          }
        };

    Class<?> adapterCls =
        Class.forName(
            "net.snytkine.springboot.wm_interceptor.WMInterceptor$SpringHttpRequestAdapter");
    java.lang.reflect.Constructor<?> ctor =
        adapterCls.getDeclaredConstructor(HttpRequest.class, byte[].class);
    ctor.setAccessible(true);
    com.github.tomakehurst.wiremock.http.Request adapter =
        (com.github.tomakehurst.wiremock.http.Request) ctor.newInstance(req, new byte[0]);

    com.github.tomakehurst.wiremock.http.QueryParameter px = adapter.queryParameter("x");
    assertNotNull(px);
    assertEquals(java.util.List.of("1", "2"), px.values());

    assertTrue(adapter.containsHeader("H"));
    assertTrue(adapter.getAllHeaderKeys().contains("H"));
    // header() returns only the first header value (adapter uses getFirst
    // internally)
    com.github.tomakehurst.wiremock.http.HttpHeader header = adapter.header("H");
    assertEquals(java.util.List.of("a"), header.values());

    // verify full header set via getHeaders()
    com.github.tomakehurst.wiremock.http.HttpHeaders all = adapter.getHeaders();
    com.github.tomakehurst.wiremock.http.HttpHeader found =
        all.all().stream().filter(h -> "H".equals(h.key())).findFirst().orElse(null);
    assertNotNull(found);
    assertEquals(java.util.List.of("a", "b"), found.values());
  }

  @Test
  void defaultHttpPortAndBase64BodyAndMissingHeader() throws Exception {
    HttpRequest req =
        new HttpRequest() {
          @Override
          public HttpMethod getMethod() {
            return HttpMethod.GET;
          }

          @Override
          public URI getURI() {
            return URI.create("http://example.com/path");
          }

          @Override
          public org.springframework.http.HttpHeaders getHeaders() {
            return new org.springframework.http.HttpHeaders();
          }
        };

    Class<?> adapterCls =
        Class.forName(
            "net.snytkine.springboot.wm_interceptor.WMInterceptor$SpringHttpRequestAdapter");
    java.lang.reflect.Constructor<?> ctor =
        adapterCls.getDeclaredConstructor(HttpRequest.class, byte[].class);
    ctor.setAccessible(true);
    com.github.tomakehurst.wiremock.http.Request adapter =
        (com.github.tomakehurst.wiremock.http.Request)
            ctor.newInstance(req, "abc".getBytes(StandardCharsets.UTF_8));

    assertEquals(80, adapter.getPort());
    assertEquals("YWJj", adapter.getBodyAsBase64());
    assertEquals("", adapter.getHeader("nope"));
  }

  @Test
  void springHttpRequestAdapterEdgeCases() throws Exception {
    HttpRequest req =
        new HttpRequest() {
          @Override
          public HttpMethod getMethod() {
            return HttpMethod.GET;
          }

          @Override
          public URI getURI() {
            return URI.create("http://example.com/path");
          }

          @Override
          public org.springframework.http.HttpHeaders getHeaders() {
            return new org.springframework.http.HttpHeaders();
          }
        };

    Class<?> adapterCls =
        Class.forName(
            "net.snytkine.springboot.wm_interceptor.WMInterceptor$SpringHttpRequestAdapter");
    java.lang.reflect.Constructor<?> ctor =
        adapterCls.getDeclaredConstructor(HttpRequest.class, byte[].class);
    ctor.setAccessible(true);
    com.github.tomakehurst.wiremock.http.Request adapter =
        (com.github.tomakehurst.wiremock.http.Request) ctor.newInstance(req, new byte[0]);

    assertFalse(adapter.containsHeader("X-FOO"));
    assertTrue(adapter.getAllHeaderKeys().isEmpty());
    assertNull(adapter.queryParameter("nope"));
    assertArrayEquals(new byte[0], adapter.getBody());
    assertEquals("", adapter.getBodyAsString());
    assertNull(adapter.getPart("p"));
    assertFalse(adapter.isBrowserProxyRequest());
    assertTrue(adapter.getOriginalRequest().isEmpty());
    assertNull(adapter.formParameter("a"));
    assertNull(adapter.formParameters());
    assertTrue(adapter.getCookies().isEmpty());
    assertEquals("https", adapter.getProtocol());
    com.github.tomakehurst.wiremock.http.HttpHeaders wh = adapter.getHeaders();
    assertNotNull(wh);
    assertTrue(wh.all().isEmpty());
  }

  @Test
  void wiremockClientHttpResponseSetHeader() throws Exception {
    Response mockResponse = mock(Response.class);
    when(mockResponse.getBody()).thenReturn(null);
    when(mockResponse.getHeaders()).thenReturn(null);

    Class<?> respCls =
        Class.forName(
            "net.snytkine.springboot.wm_interceptor.WMInterceptor$WiremockClientHttpResponse");
    java.lang.reflect.Constructor<?> ctor = respCls.getDeclaredConstructor(Response.class);
    ctor.setAccessible(true);
    org.springframework.http.client.ClientHttpResponse resp =
        (org.springframework.http.client.ClientHttpResponse) ctor.newInstance(mockResponse);

    // call setHeader via reflection
    java.lang.reflect.Method setHeader =
        respCls.getDeclaredMethod("setHeader", String.class, String.class);
    setHeader.setAccessible(true);
    setHeader.invoke(resp, "K", "V");

    assertEquals("V", resp.getHeaders().getFirst("K"));
  }

  @Test
  void interceptForwardsToExecutionWhenNoMock() throws Exception {
    WireMockProperties props = new WireMockProperties();
    WMInterceptor interceptor =
        new WMInterceptor(new com.github.tomakehurst.wiremock.core.WireMockConfiguration(), props);

    DirectCallHttpServer mockServer = mock(DirectCallHttpServer.class);
    Response mockResponse = mock(Response.class);
    when(mockResponse.wasConfigured()).thenReturn(false);
    when(mockServer.stubRequest(any())).thenReturn(mockResponse);

    java.lang.reflect.Field f = WMInterceptor.class.getDeclaredField("directCallHttpServer");
    f.setAccessible(true);
    f.set(interceptor, mockServer);

    // create a response returned by execution
    ClientHttpResponse execResp =
        new ClientHttpResponse() {
          @Override
          public org.springframework.http.HttpHeaders getHeaders() {
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.set("X-REAL", "yes");
            return h;
          }

          @Override
          public java.io.InputStream getBody() throws IOException {
            return new ByteArrayInputStream("real".getBytes(StandardCharsets.UTF_8));
          }

          @Override
          public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return org.springframework.http.HttpStatusCode.valueOf(418);
          }

          @Override
          public String getStatusText() throws IOException {
            return "I'M A TEAPOT";
          }

          @Override
          public void close() {
            // no-op
          }
        };

    final boolean[] called = {false};
    ClientHttpRequestExecution exec =
        (request, body) -> {
          called[0] = true;
          return execResp;
        };

    ClientHttpResponse resp =
        interceptor.intercept(simpleRequest("http://localhost/test"), new byte[0], exec);
    assertTrue(called[0]);
    assertEquals(418, resp.getStatusCode().value());
    assertEquals("yes", resp.getHeaders().getFirst("X-REAL"));
    String body = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("real", body);
  }
}
