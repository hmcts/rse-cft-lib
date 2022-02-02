package uk.gov.hmcts.rse.ccd.lib.injected;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Set the thread name for all requests
 * to make it easy to identify relevant threads when debugging.
 */
@Order(value= Ordered.HIGHEST_PRECEDENCE)
@Component
public class ThreadNamer extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String name = Thread.currentThread().getName();
        Thread.currentThread().setName("***CFT LIB***");
        try {
            filterChain.doFilter(request, response);
        } finally {
            Thread.currentThread().setName(name);
        }
    }
}
