package uk.gov.hmcts.rse.ccd.lib;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.reform.idam.client.IdamApi;

/**
 * Implements URL remappings handled by the CCD API Gateway.
 */
@Order(value = Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass({IdamApi.class, HttpServletRequest.class})
@Component
public class URLRewriter extends OncePerRequestFilter {

    @Autowired
    private final IdamApi idam;

    private final String name;

    @Autowired
    public URLRewriter(IdamApi idam,
                       @Value("${rse.lib.service_name:***CFT lib***}") String name) {
        this.idam = idam;
        this.name = name;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String name = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("*** " + this.name);
            filterChain.doFilter(new Rewriter(request), response);
        } finally {
            Thread.currentThread().setName(name);
        }
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
            // CCD Gateway strips this path.
            if (url.startsWith("/data")) {
                url = url.replaceFirst("/data", "");
            }
            // Gateway replaces placeholder userIDs.
            if (url.contains("/:uid/")) {
                url = substituteUserId(url);
            }
            return url;
        }

        private String substituteUserId(String url) {
            var req = (HttpServletRequest) getRequest();
            var auth = req.getHeader("Authorization");
            var info = idam.retrieveUserInfo(auth);
            return url.replace(":uid", info.getUid());
        }
    }
}
