package com.hieulc.insightragretrieval.base;

import java.time.Duration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.neo4j.Neo4jContainer;

public class AbstractNeo4jIT {
  @ServiceConnection
  protected static Neo4jContainer neo4j =
      new Neo4jContainer("neo4j:5.26")
          .withoutAuthentication()
          .withStartupTimeout(Duration.ofMinutes(3))
          .withPlugins("apoc")
          .withReuse(true);

  static {
    try {
      neo4j.start();
    } catch (Exception e) {
      System.err.println(neo4j.getLogs());
      throw new RuntimeException(e);
    }
  }
}
