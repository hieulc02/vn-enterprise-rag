package com.hieulc.coreinfrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinIOProperties(String endpoint, Credentials credentials) {
  public record Credentials(String username, String password) {}
}
