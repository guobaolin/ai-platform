package com.example.aip.platform.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Inner API 鉴权：只对 /inner/** 生效，校验共享静态令牌。
 * Console API 原型期无鉴权，不走此 Filter。
 */
@Configuration
public class InnerAuthConfig {

    @Bean
    public FilterRegistrationBean<InnerTokenFilter> innerTokenFilter(PlatformProperties props) {
        FilterRegistrationBean<InnerTokenFilter> reg = new FilterRegistrationBean<>(new InnerTokenFilter(props.getRuntimeToken()));
        reg.addUrlPatterns("/inner/*");
        reg.setOrder(1);
        return reg;
    }

    static class InnerTokenFilter extends OncePerRequestFilter {
        private final String token;

        InnerTokenFilter(String token) {
            this.token = token;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth == null || !("Bearer " + token).equals(auth.trim())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"invalid or missing bearer token\"}");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
