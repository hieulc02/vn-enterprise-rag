package com.hieulc.insightragworker.config;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;

public interface TestListener {
    void onMessage(Message message, Channel channel) throws Exception;
}
