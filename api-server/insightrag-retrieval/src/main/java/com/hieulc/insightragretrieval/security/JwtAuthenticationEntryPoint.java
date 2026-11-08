package com.hieulc.insightragretrieval.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final String realmName;
  private final ObjectMapper mapper;

  public JwtAuthenticationEntryPoint(
      @Value("${keycloak.realm:insightrag}") String realmName, ObjectMapper mapper) {
    this.mapper = mapper;
    this.realmName = realmName;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("WWW-Authenticate", "Bearer realm=\"" + realmName + "\"");

    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, authException.getMessage());
    problemDetail.setTitle("Authentication Failed");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    mapper.writeValue(response.getOutputStream(), problemDetail);
  }
}
