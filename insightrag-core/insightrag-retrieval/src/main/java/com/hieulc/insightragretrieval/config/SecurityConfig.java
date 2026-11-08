package com.hieulc.insightragretrieval.config;

import com.hieulc.insightragretrieval.security.JwtAuthenticationEntryPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  @Value("${insightrag.cors.allowed-origins}")
  private final List<String> allowedOrigins;

  private final JwtAuthenticationEntryPoint authenticationEntryPoint;

  @SuppressWarnings("RedundantThrows")
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers("/api/v1/chat/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(Customizer.withDefaults())
                    // catches the "Invalid/Expired JWT" thrown early by the
                    // BearerTokenAuthenticationFilter
                    .authenticationEntryPoint(authenticationEntryPoint))
        .exceptionHandling(
            // catches the "Missing JWT" thrown later by the AuthorizationFilter
            exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint));

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration corsConfiguration = new CorsConfiguration();

    corsConfiguration.setAllowedOrigins(allowedOrigins);
    corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    corsConfiguration.setExposedHeaders(
        List.of("RateLimit-Remaining", "Retry-After", "WWW-Authenticate"));

    // OPTIONS for the browser preflight request
    corsConfiguration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    corsConfiguration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", corsConfiguration);
    return source;
  }
}
