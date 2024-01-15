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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.reform.idam.client.IdamApi;

/**
 * Names threads for easier debugging.
 * Implements URL remappings handled by the CCD API Gateway.
 * TODO: delete this and consolidate the springBoot3 sourceset when we can drop support for Spring Boot 2.
 */
@Order(value = Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass({IdamApi.class, HttpServletRequest.class})
@ConditionalOnExpression("#{T(org.springframework.boot.SpringBootVersion).getVersion().startsWith('2')}")
@Component
public class SpringBoot2RequestFilter extends OncePerRequestFilter {

    @Autowired
    private final IdamApi idam;

    private final String name;

    private final boolean isCCD;

    @Autowired
    public SpringBoot2RequestFilter(IdamApi idam,
                                    @Value("${rse.lib.service_name:***CFT lib***}") String name) {
        this.idam = idam;
        this.name = name;
        this.isCCD = name.toLowerCase().contains("ccd");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String name = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("*** " + this.name);
            // Only emulate the CCD gateway for CCD services
            request = isCCD ? new Rewriter(request) : request;
            filterChain.doFilter(request, response);
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
