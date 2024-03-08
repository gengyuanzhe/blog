package org.example;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * AccessLogFilter
 *
 * @author g00471473
 * @since 2024-02-29
 */
@Slf4j
public class AccessLogFilter implements Filter {
    @Override public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        log.warn("before doFilter");
        chain.doFilter(request, response);
        log.warn("after doFilter");
    }

    @Override public void destroy() {
        Filter.super.destroy();
    }
}
