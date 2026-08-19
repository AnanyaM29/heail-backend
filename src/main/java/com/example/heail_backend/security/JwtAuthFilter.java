package com.example.heail_backend.security;

import com.example.heail_backend.entity.User;
import com.example.heail_backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtService.isAccessTokenValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        var claims = jwtService.parseAccessToken(token);
        String email = claims.getSubject();
        String role  = claims.get("role", String.class);

        // Blacklist/soft-delete must take effect immediately, not just once the (up to
        // 24h-lived) access token expires — so re-check current status on every request
        // rather than trusting what was true when the token was minted.
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty() || !user.get().isActive() || user.get().getDeletedAt() != null) {
            chain.doFilter(request, response);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
