package com.alexandria.user.auth;

import com.alexandria.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OidcUserFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final String userIdClaim;
    private final String usernameClaim;

    public OidcUserFilter(
            UserService userService,
            @Value("${app.auth.user-id-claim}") String userIdClaim,
            @Value("${app.auth.username-claim}") String usernameClaim) {
        this.userService = userService;
        this.userIdClaim = userIdClaim;
        this.usernameClaim = usernameClaim;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String oidcSubject = jwt.getClaimAsString(userIdClaim);
            String username = jwt.getClaimAsString(usernameClaim);
            String email = jwt.getClaimAsString("email");

            userService.findOrCreateByOidcSubject(oidcSubject, username, email);
        }

        filterChain.doFilter(request, response);
    }
}
