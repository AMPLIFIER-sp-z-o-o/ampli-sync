package com.ampliapps.amplisync;

import jakarta.annotation.Priority;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

@Priority(value = 2)
public class RestAuthenticationFilter implements Filter {

    // Modern Set initialization for better performance and thread safety
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/ampli-sync",
            "/ampli-sync/",
            "/app/ampli-sync",
            "/app/ampli-sync/",
            "/ampli_sync_war/ampli-sync",
            "/ampli_sync_war/ampli-sync/"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpServletRequest &&
                response instanceof HttpServletResponse httpServletResponse) {

            AuthenticationService authenticationService = new AuthenticationService();
            String requestURI = httpServletRequest.getRequestURI();

            Logs.write(Logs.Level.DEBUG, "RestAuthenticationFilter -> doFilter Request URI: " + requestURI);

            if("true".equals(System.getenv("AUTH_DISABLE"))){
                Logs.write(Logs.Level.WARN,"Authentication disabled by env: AUTH_DISABLED=true");
                chain.doFilter(request, response);
                return;
            }

            boolean allowed = ALLOWED_PATHS.contains(requestURI);
            Logs.write(Logs.Level.DEBUG, "RestAuthenticationFilter -> doFilter Is path un-secure: " + (allowed ? "Yes" : "No"));

            boolean authenticationStatus = allowed || authenticationService.authenticate(httpServletRequest);

            if (authenticationStatus) {
                chain.doFilter(request, response);
            } else {
                httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Optional in Jakarta EE if not needed
    }

    @Override
    public void destroy() {
        // Optional in Jakarta EE if not needed
    }
}
