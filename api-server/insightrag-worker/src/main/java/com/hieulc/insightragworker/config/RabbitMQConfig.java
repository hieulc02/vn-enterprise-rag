package com.hieulc.insightragworker.config;

import com.hieulc.coreinfrastructure.exception.StorageProviderException;
import com.hieulc.insightragworker.config.properties.RabbitMqProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.*;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

@Configuration
@RequiredArgsConstructor
@EnableRabbit
public class RabbitMqConfig {

  private final RabbitMqProperties rabbitMqProperties;

  /**
   * Direct Exchange is highly effective in case of granular routing, multi-queue isolation, and
   * predictable handling failure
   */
  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(rabbitMqProperties.dlExchange());
  }

  @Bean
  public Queue deadLetterQueue() {
    return QueueBuilder.durable(rabbitMqProperties.dlQueue()).build();
  }

  @Bean
  public Binding deadLetterBinding(DirectExchange deadLetterExchange, Queue deadLetterQueue) {
    return BindingBuilder.bind(deadLetterQueue)
        .to(deadLetterExchange)
        .with(rabbitMqProperties.dlRoutingKey());
  }

  @Bean
  public DirectExchange mainExchange() {
    return new DirectExchange(rabbitMqProperties.mainExchange());
  }

  @Bean
  public Queue mainQueue() {
    return QueueBuilder.durable(rabbitMqProperties.mainQueue())
        .deadLetterExchange(rabbitMqProperties.dlExchange())
        .deadLetterRoutingKey(rabbitMqProperties.dlRoutingKey())
        .build();
  }

  @Bean
  public Binding mainBinding(DirectExchange mainExchange, Queue mainQueue) {
    return BindingBuilder.bind(mainQueue)
        .to(mainExchange)
        .with(rabbitMqProperties.mainRoutingKey());
  }

  @Bean
  public JacksonJsonMessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  /**
   * Default behavior for exhausted retries
   *
   * @param amqpTemplate - the default Spring-initialized bean for AMQP-supported processes
   * @return republish recovered messages to a specified exchanged
   */
  @Bean
  public MessageRecoverer messageRecoverer(AmqpTemplate amqpTemplate) {
    RepublishMessageRecoverer recoverer =
        new RepublishMessageRecoverer(
            amqpTemplate, rabbitMqProperties.dlExchange(), rabbitMqProperties.dlRoutingKey());

    recoverer.includeStackTrace(true);
    return recoverer;
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      JacksonJsonMessageConverter jsonMessageConverter,
      SimpleRabbitListenerContainerFactoryConfigurer configurer,
      MethodInterceptor retryOperationsInterceptor) {

    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);

    factory.setMessageConverter(jsonMessageConverter);
    factory.setAdviceChain(retryOperationsInterceptor);

    return factory;
  }

  @Bean
  public MethodInterceptor retryOperationsInterceptor(MessageRecoverer messageRecoverer) {
    List<Class<? extends Throwable>> nonRetryableException = new ArrayList<>();
    nonRetryableException.add(IllegalArgumentException.class);

    List<Class<? extends Throwable>> retryableException = new ArrayList<>();
    retryableException.add(StorageProviderException.class);

    RetryPolicy policy =
        RetryPolicy.builder().includes(retryableException).excludes(nonRetryableException).build();

    return RetryInterceptorBuilder.stateless()
        .backOffOptions(1, 2.0, 10)
        .maxRetries(3)
        .retryPolicy(policy)
        .recoverer(messageRecoverer)
        .build();
  }
}
