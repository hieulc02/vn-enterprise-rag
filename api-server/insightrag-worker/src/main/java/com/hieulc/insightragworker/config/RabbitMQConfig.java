package com.hieulc.insightragworker.config;

import com.hieulc.insightragworker.exception.appli.DepartmentInvalidException;
import com.hieulc.insightragworker.exception.infra.StorageProviderException;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.core.retry.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.*;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

import java.util.ArrayList;
import java.util.List;


@Configuration
public class RabbitMQConfig {

    public static final String DLQ_QUEUE = "dlq_direct_queue";
    public static final String DLX_EXCHANGE = "dlx.direct.exchange";
    public static final String DLX_ROUTING_KEY = "worker.failed";

    public static final String MAIN_QUEUE = "main_direct_queue";
    public static final String MAIN_EXCHANGE = "main.direct.exchange";
    public static final String MAIN_ROUTING_KEY = "worker.process";

    @Bean
    public DirectExchange deadLetterExchange(){
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue(){
        return QueueBuilder.durable(DLQ_QUEUE)
                .build();
    }

    @Bean
    public Binding deadLetterBinding(DirectExchange deadLetterExchange, Queue deadLetterQueue){
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLX_ROUTING_KEY);
    }

    @Bean
    public DirectExchange mainExchange(){
        return new DirectExchange(MAIN_EXCHANGE);
    }

    @Bean
    public Queue mainQueue(){
        return QueueBuilder.durable(MAIN_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding mainBinding(DirectExchange mainExchange, Queue mainQueue){
        return BindingBuilder.bind(mainQueue).to(mainExchange).with(MAIN_ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Default behavior for exhausted retries
     * @param amqpTemplate
     * @return republish recovered messages to a specified exchanged
     */
    @Bean
    public MessageRecoverer messageRecoverer(AmqpTemplate amqpTemplate){
        return new RepublishMessageRecoverer(amqpTemplate, DLX_EXCHANGE, DLX_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter jsonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MethodInterceptor retryOperationsInterceptor
    ){
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        factory.setMessageConverter(jsonMessageConverter);
//        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setAdviceChain(retryOperationsInterceptor);

        return factory;
    }

    @Bean
    public MethodInterceptor retryOperationsInterceptor(
            MessageRecoverer messageRecoverer
    ) {
        List<Class<? extends Throwable>> nonRetryableException = new ArrayList<>();
        nonRetryableException.add(DepartmentInvalidException.class);
        nonRetryableException.add(IllegalArgumentException.class);

        List<Class<? extends Throwable>> retryableException = new ArrayList<>();
        retryableException.add(StorageProviderException.class);

        RetryPolicy policy = RetryPolicy.builder()
                .includes(retryableException)
                .excludes(nonRetryableException)
                .build();

        return RetryInterceptorBuilder.stateless()
                .backOffOptions(1, 2.0, 10)
                .maxRetries(3)
                .retryPolicy(policy)
                .recoverer(messageRecoverer)
                .build();
    }
}
