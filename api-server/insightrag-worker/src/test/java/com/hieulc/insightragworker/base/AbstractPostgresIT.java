package com.hieulc.insightragworker.base;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractPostgresIT {

  @ServiceConnection
  protected static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
          .withCommand("postgres", "-c", "wal_level=logical");

  static {
    try {
      postgres.start();
    } catch (Exception e) {
      System.err.println(postgres.getLogs());
      throw new RuntimeException(e);
    }
  }
}
