package com.hieulc.insightragworker.config;

import com.hieulc.insightragworker.exception.infra.StorageProviderException;
import com.rabbitmq.client.Channel;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.aop.framework.*;


import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class RabbitMQConfigTest {

    private final RabbitMQConfig rabbitMQConfig = new RabbitMQConfig();

    @Mock
    private MessageRecoverer messageRecoverer;

    @Test
    @DisplayName("Should retry 3 times when StorageProviderException is thrown")
    void shouldRetry_whenRetryableExceptionThrown() throws Exception{
        MethodInterceptor interceptor = rabbitMQConfig.retryOperationsInterceptor(messageRecoverer);

        TestListener test = mock(TestListener.class);

        Throwable rootCause = new RuntimeException("Connection time out");

        doThrow(new StorageProviderException("MinIO is down!", rootCause))
                .when(test).onMessage(any(Message.class), any(Channel.class));

        ProxyFactory proxyFactory = new ProxyFactory(test);
        proxyFactory.addAdvice(interceptor);
        TestListener proxy = (TestListener) proxyFactory.getProxy();

        Message message = mock(Message.class);
        Channel channel = mock(Channel.class);

        proxy.onMessage(message, channel);

        verify(test, times(4)).onMessage(message, channel); //1 attempt, 3 retries, 4 in total
    }

    @Test
    @DisplayName("Should send to DLQ after 3 retries when StorageProviderException is thrown")
    void shouldSentToDLQ_whenRetryableExceptionThrown() throws Exception{
        AmqpTemplate amqpTemplate = mock(AmqpTemplate.class);

        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
          amqpTemplate, RabbitMQConfig.DLX_EXCHANGE, RabbitMQConfig.DLX_ROUTING_KEY
        );
        Message message = new Message("Test payload".getBytes(), new MessageProperties());
        Throwable cause = new RuntimeException("MinIO is down!");
        recoverer.recover(message, cause);

        verify(amqpTemplate, times(1)).send(eq(RabbitMQConfig.DLX_EXCHANGE), eq(RabbitMQConfig.DLX_ROUTING_KEY), any(Message.class));
    }
}