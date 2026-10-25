package io.github.frankie7techy.haraka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.frankie7techy.haraka.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless bearer-token security. Public surface (docs, health, metrics) is explicitly
 * listed; everything else requires authentication. Authentication failures and denied
 * access both answer with RFC 7807 problem responses.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/",
            "/error",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                           ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint entryPoint = problemEntryPoint(objectMapper);
        AccessDeniedHandler deniedHandler = problemAccessDeniedHandler(objectMapper);

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private AuthenticationEntryPoint problemEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authenticationException) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                    "Authentication required: send 'Authorization: Bearer <token>'");
            problem.setTitle("Unauthorized");
            problem.setProperty("code", "UNAUTHENTICATED");
            SecurityProblemWriters.write(response, objectMapper, problem);
        };
    }

    private AccessDeniedHandler problemAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this operation");
            problem.setTitle("Forbidden");
            problem.setProperty("code", "ACCESS_DENIED");
            SecurityProblemWriters.write(response, objectMapper, problem);
        };
    }

    static final class SecurityProblemWriters {
        private SecurityProblemWriters() {
        }

        static void write(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper,
                          ProblemDetail problem) throws java.io.IOException {
            response.setStatus(problem.getStatus());
            response.setContentType("application/problem+json");
            objectMapper.writeValue(response.getOutputStream(), problem);
        }
    }
}
