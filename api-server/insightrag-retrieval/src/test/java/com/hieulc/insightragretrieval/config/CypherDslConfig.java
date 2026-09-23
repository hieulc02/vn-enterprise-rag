package com.hieulc.insightragretrieval.config;

import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class CypherDslConfig {
  @Bean
  public Configuration cypherDslConfiguration() {
    return Configuration.newConfig().withDialect(Dialect.NEO4J_5).build();
  }

  @Bean
  public Renderer cypherRenderer(Configuration cypherDslConfiguration) {
    return Renderer.getRenderer(cypherDslConfiguration);
  }
}
