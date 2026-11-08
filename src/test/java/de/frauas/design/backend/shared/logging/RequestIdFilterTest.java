package de.frauas.design.backend.shared.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestIdFilter")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("keeps a valid id, puts it into the MDC during the request and echoes it")
    void keepsValidId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        request.addHeader(RequestIdFilter.HEADER, "fe-1234.abc_X");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seen.get()).isEqualTo("fe-1234.abc_X");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("fe-1234.abc_X");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "has space", "line\nbreak", "id-that-is-longer-than-sixty-four-characters-is-replaced-by-a-new-uuid"})
    @DisplayName("replaces a missing or unsafe id with a new UUID")
    void generatesId(String header) {
        String id = RequestIdFilter.resolve(header);

        assertThat(id).isNotEqualTo(header).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("clears the MDC even when the request fails")
    void clearsMdcOnError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // expected
        }
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
    }
}
