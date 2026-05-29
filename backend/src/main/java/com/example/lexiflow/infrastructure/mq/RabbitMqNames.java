package com.example.lexiflow.infrastructure.mq;

public final class RabbitMqNames {

    public static final String AGENT_EXCHANGE = "lexiflow.agent.exchange";
    public static final String CONTRACT_REVIEW_QUEUE = "contract.review.queue";
    public static final String DOCUMENT_INGEST_QUEUE = "document.ingest.queue";
    public static final String TOOL_RETRY_QUEUE = "tool.retry.queue";
    public static final String APPROVAL_EVENT_QUEUE = "approval.event.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";

    public static final String CONTRACT_REVIEW_ROUTING_KEY = "contract.review";
    public static final String DOCUMENT_INGEST_ROUTING_KEY = "document.ingest";
    public static final String TOOL_RETRY_ROUTING_KEY = "tool.retry";
    public static final String APPROVAL_EVENT_ROUTING_KEY = "approval.event";
    public static final String NOTIFICATION_ROUTING_KEY = "notification";

    private RabbitMqNames() {
    }
}

