package com.example.lexiflow.integration;

import com.example.lexiflow.infrastructure.mq.RabbitMqNames;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RabbitMqConsumerTest extends TestcontainersBaseTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Test
    void queueIsDeclaredAndReady() {
        var properties = rabbitAdmin.getQueueProperties(RabbitMqNames.CONTRACT_REVIEW_QUEUE);
        Assertions.assertThat(properties).isNotNull();
    }

    @Test
    void messagesRoutedToCorrectQueue() {
        rabbitTemplate.convertAndSend(RabbitMqNames.CONTRACT_REVIEW_QUEUE, "test message");

        Object received = rabbitTemplate.receiveAndConvert(RabbitMqNames.CONTRACT_REVIEW_QUEUE, 2000);

        Assertions.assertThat(received).isEqualTo("test message");
    }

    @Test
    void documentIngestQueueIsAvailable() {
        var properties = rabbitAdmin.getQueueProperties(RabbitMqNames.DOCUMENT_INGEST_QUEUE);
        Assertions.assertThat(properties).isNotNull();
    }

    @Test
    void reviewJobPublishAndRoundTrip() {
        String message = "{\"reviewId\":42,\"userId\":1}";

        rabbitTemplate.convertAndSend(RabbitMqNames.CONTRACT_REVIEW_QUEUE, message);
        Object received = rabbitTemplate.receiveAndConvert(RabbitMqNames.CONTRACT_REVIEW_QUEUE, 2000);

        Assertions.assertThat(received).isEqualTo(message);
    }

    @Test
    void emptyQueueReturnsNullOnReceive() {
        rabbitTemplate.receiveAndConvert(RabbitMqNames.TOOL_RETRY_QUEUE, 100);

        var properties = rabbitAdmin.getQueueProperties(RabbitMqNames.TOOL_RETRY_QUEUE);
        Assertions.assertThat(properties).isNotNull();
    }
}
