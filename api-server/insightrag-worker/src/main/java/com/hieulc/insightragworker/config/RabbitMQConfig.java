package com.hieulc.insightragworker.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;


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
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);

        return QueueBuilder.durable(MAIN_QUEUE)
                .withArguments(args)
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

}
