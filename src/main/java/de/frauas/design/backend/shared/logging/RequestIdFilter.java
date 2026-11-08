package de.frauas.design.backend.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id that is shared by the whole stack.
 *
 * <p>The frontend sends an {@code X-Request-ID} header with each call, and the DaaS
 * backend passes its own request id on when it calls this service. That id is put
 * into the logging context ({@code %X{requestId}} in the log pattern) and echoed in
 * the response, so one user action can be followed through the logs of all three
 * services. A request without a usable header gets a new id.</p>
 *
 * <p>Runs first (before rate limiting and Spring Security), so rejected requests
 * are logged with their id too.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Header read from requests and written to responses. */
    public static final String HEADER = "X-Request-ID";

    /** Key of the id in the logging context (MDC). */
    public static final String MDC_KEY = "requestId";

    /** Accepted ids: short, and only characters that are safe in a log line. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Returns the given id if it is valid, otherwise a new one.
     *
     * @param header value of the {@code X-Request-ID} header, may be {@code null}
     * @return the id to use for this request
     */
    static String resolve(String header) {
        if (header != null && VALID_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
