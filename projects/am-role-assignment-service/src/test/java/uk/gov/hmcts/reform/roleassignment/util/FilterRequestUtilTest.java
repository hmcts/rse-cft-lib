package uk.gov.hmcts.reform.roleassignment.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.UnprocessableEntityException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
class FilterRequestUtilTest {

    @Mock
    HttpServletRequest httpServletRequest;

    @Mock
    HttpServletResponse httpServletResponse;

    @Mock
    FilterChain filterChain;

    @Mock
    CorrelationInterceptorUtil correlationInterceptorUtil;


    @InjectMocks
    @Spy
    private FilterRequestUtil sut = new FilterRequestUtil();

    MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void doFilterInternal() throws ServletException, IOException {
        when(correlationInterceptorUtil.preHandle(any(HttpServletRequest.class)))
            .thenReturn("a5cff648-84b6-404d-83d6-f86b526cc59b");

        when(httpServletRequest.getInputStream()).thenReturn(request.getInputStream());

        sut.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);
        verify(correlationInterceptorUtil, times(1))
            .preHandle(any(HttpServletRequest.class));
        verify(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        //verify(httpServletResponse).addHeader(anyString(), anyString());
    }

    @Test
    void doFilterInternal_throwsBadRequest() throws Exception {
        when(correlationInterceptorUtil.preHandle(any(HttpServletRequest.class)))
            .thenReturn("a5cff648-84b6-404d-83d6");
        when(httpServletRequest.getInputStream()).thenReturn(request.getInputStream());
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.doFilterInternal(httpServletRequest, httpServletResponse, filterChain)
        );
        verify(correlationInterceptorUtil, times(1))
            .preHandle(any(HttpServletRequest.class));
    }

    @Test
    void doFilterInternalException() throws IOException, ServletException {
        when(correlationInterceptorUtil.preHandle(any(HttpServletRequest.class)))
            .thenReturn("a5cff648-84b6-404d-83d6-f86b526cc59b");

        when(httpServletRequest.getInputStream()).thenReturn(request.getInputStream());

        doThrow(ServletException.class).when(filterChain).doFilter(any(), any());

        Assertions.assertThrows(UnprocessableEntityException.class, () ->
            sut.doFilterInternal(httpServletRequest, httpServletResponse, filterChain)
        );
    }
}
