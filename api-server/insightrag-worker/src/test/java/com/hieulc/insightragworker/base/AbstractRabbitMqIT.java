package com.hieulc.insightragworker.base;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.rabbitmq.RabbitMQContainer;

public abstract class AbstractRabbitMqIT {

  @ServiceConnection
  protected static RabbitMQContainer rabbitMQ =
      new RabbitMQContainer("rabbitmq:4.3.0-management-alpine");

  static {
    try {
      rabbitMQ.start();
    } catch (Exception e) {
      System.err.println(rabbitMQ.getLogs());
      throw new RuntimeException(e);
    }
  }
}
