package com.hieulc.insightragingestion.base;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractKafkaIT {

  @ServiceConnection
  protected static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.0"));

  static {
    try {
      kafka.start();
    } catch (Exception e) {
      System.err.println(kafka.getLogs());
      throw new RuntimeException(e);
    }
  }
}
