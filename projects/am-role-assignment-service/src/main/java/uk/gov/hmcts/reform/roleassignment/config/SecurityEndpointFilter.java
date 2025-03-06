package uk.gov.hmcts.reform.roleassignment.config;

import feign.FeignException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.UnauthorizedException;
import uk.gov.hmcts.reform.roleassignment.util.Constants;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class SecurityEndpointFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION = "Authorization";

    @Autowired
    IdamApi idamApi;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            if (StringUtils.isNotEmpty(request.getRequestURI()) && request.getRequestURI().contains("/am")) {
                logger.debug("The Service Auth token length is: "
                                 + request.getHeader(Constants.SERVICE_AUTHORIZATION2).length());
                logger.debug("The User Auth token length : "
                                 + request.getHeader(AUTHORIZATION).length());
                logger.debug("The User Auth token contains 'Bearer '? : "
                                 + request.getHeader(AUTHORIZATION).contains("Bearer "));
                if (logger.isDebugEnabled()) {
                    UserInfo userInfo = idamApi.retrieveUserInfo(request.getHeader(AUTHORIZATION));
                    logger.debug(userInfo);
                }
            }
        } catch (Exception e) {
            logger.info("Exception while processing the user or service tokens : " + e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            Throwable throwable = e.getCause();
            if (throwable instanceof FeignException.FeignClientException) {
                FeignException.FeignClientException feignClientException =
                    (FeignException.FeignClientException) throwable;
                response.setStatus(feignClientException.status());
                return;
            } else if (e instanceof UnauthorizedException) {
                logger.error("Authorisation exception", e);
                response.sendError(HttpStatus.FORBIDDEN.value(), "Access Denied");
                return;
            }
            throw e;
        }
    }
}
