package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Implements URL remappings handled by the CCD API Gateway.
 */
@Order(value= Ordered.HIGHEST_PRECEDENCE)
@Component
public class URLRewriter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        filterChain.doFilter(new Rewriter(request), response);
    }

    class Rewriter extends HttpServletRequestWrapper {
        public Rewriter(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getServletPath() {
            return process(super.getServletPath());
        }

        @Override
        public String getRequestURI() {
            return process(super.getRequestURI());
        }

        private String process(String url) {
            if (url.startsWith("/data")) {
                return url.replaceFirst("/data", "");
            }
            return url;
        }
    }
}
