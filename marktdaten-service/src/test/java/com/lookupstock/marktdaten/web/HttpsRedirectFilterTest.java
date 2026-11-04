package com.lookupstock.marktdaten.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpsRedirectFilterTest {

    private final HttpsRedirectFilter filter = new HttpsRedirectFilter();

    private static MockHttpServletRequest request(String proto) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("Host", "example.herokuapp.com");
        request.setQueryString("a=1");
        if (proto != null) {
            request.addHeader("X-Forwarded-Proto", proto);
        }
        return request;
    }

    @Test
    void httpWirdMit308AufHttpsUmgeleitet() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("http"), response, chain);

        assertThat(response.getStatus()).isEqualTo(308);
        assertThat(response.getHeader("Location")).isEqualTo("https://example.herokuapp.com/actuator/health?a=1");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void httpsWirdDurchgelassen() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("https"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Location")).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ohneHeaderWirdNichtUmgeleitet() throws Exception {
        // lokal und in den Tests gibt es keinen Router, der X-Forwarded-Proto setzt
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ersterWertImHeaderZaehlt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("http, https"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(308);
    }
}
