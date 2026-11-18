package com.sskings.bolso.config;

import com.sskings.bolso.security.JwtAuthFilter;
import com.sskings.bolso.security.JwtService;
import com.sskings.bolso.security.RateLimitFilter;
import com.sskings.bolso.security.RequestSizeLimitFilter;
import com.sskings.bolso.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * API sem estado: token no cabeçalho Authorization (sem cookie → sem CSRF), nada de sessão. Tudo exige login, menos
 * cadastro, login e o health check. Respostas da API com CSP "nada carrega", sem cache e sem referrer.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Extrato de até 1 MB (mais o envelope multipart); a importação de 2.000 linhas em JSON cabe nisso. */
    static final long IMPORT_MAX_BYTES = 1_100_000;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(11);
    }

    @Bean
    JwtAuthFilter jwtAuthFilter(JwtService jwt, UserRepository users) {
        return new JwtAuthFilter(jwt, users);
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtAuthFilter jwtAuthFilter, BolsoProperties props, Clock clock,
                            @Value("${springdoc.swagger-ui.enabled:false}") boolean docs) throws Exception {
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        return http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())             // bean "corsConfigurationSource" abaixo
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(path.matcher("/api/**"),
                                new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> write(res, 403, "Access denied")))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                     .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                     .requestMatchers("/error").permitAll();
                    if (docs) a.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    a.anyRequest().authenticated();
                })
                .addFilterBefore(new RequestSizeLimitFilter(props.maxRequestBytes(), IMPORT_MAX_BYTES,
                        p -> p.matches("/api/workspaces/[^/]+/imports/(preview|commit)")), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(props.rateLimit().authPerMinute(), clock), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** CORS só para as origens configuradas (o front); nunca "*". */
    @Bean
    CorsConfigurationSource corsConfigurationSource(BolsoProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.cors().allowedOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        c.setAllowCredentials(false);
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }

    private static void write(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
