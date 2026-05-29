package com.example.lexiflow.llm.service;

import com.example.lexiflow.config.LlmProperties;
import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.ChatResponse;
import com.example.lexiflow.llm.model.EmbeddingRequest;
import com.example.lexiflow.llm.model.EmbeddingResponse;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.model.ToolCallRequest;
import com.example.lexiflow.llm.model.ToolCallResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(prefix = "lexiflow.llm", name = "provider", havingValue = "deepseek")
public class DeepSeekLlmGateway implements LlmGateway {

    private static final String PROVIDER = "deepseek";
    private static final String DEFAULT_CHAT_MODEL = "deepseek-v4-flash";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v4";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient deepSeekClient;
    private final RestClient dashScopeClient;

    public DeepSeekLlmGateway(LlmProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.deepSeekClient = restClientBuilder.clone()
                .baseUrl(defaultIfBlank(properties.deepseek().baseUrl(), "https://api.deepseek.com"))
                .defaultHeader(HttpHeaders.AUTHORIZATION, bearer(required(properties.deepseek().apiKey(), "DEEPSEEK_API_KEY")))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.dashScopeClient = restClientBuilder.clone()
                .baseUrl(defaultIfBlank(properties.dashscope().baseUrl(), "https://dashscope.aliyuncs.com/compatible-mode/v1"))
                .defaultHeader(HttpHeaders.AUTHORIZATION, bearer(required(properties.dashscope().apiKey(), "DASHSCOPE_API_KEY")))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Map<String, Object> response = postDeepSeek(chatPayload(request, false, null, null));
        JsonNode root = objectMapper.valueToTree(response);
        JsonNode choice = firstChoice(root);
        String content = choice.path("message").path("content").asText("");
        return new ChatResponse(content, usage(root), PROVIDER, root.path("model").asText(chatModel(request.model())));
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<String> tokenConsumer) {
        postDeepSeekStream(chatPayload(request, true, null, null), tokenConsumer);
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embeddingModel(request.model()));
        body.put("input", request.texts());
        body.put("encoding_format", "float");
        Integer dimensions = properties.dashscope().embeddingDimensions();
        if (dimensions != null) {
            body.put("dimensions", dimensions);
        }

        Map<String, Object> response;
        try {
            Object rawResponse = dashScopeClient.post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(Object.class);
            response = objectMapper.convertValue(rawResponse, MAP_TYPE);
        } catch (RestClientException ex) {
            throw new LlmGatewayException("DashScope embedding request failed", ex);
        }

        JsonNode root = objectMapper.valueToTree(response);
        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : item.path("embedding")) {
                vector.add(value.asDouble());
            }
            embeddings.add(vector);
        }
        return new EmbeddingResponse(embeddings, embeddingUsage(root), "dashscope", root.path("model").asText(embeddingModel(request.model())));
    }

    @Override
    public StructuredOutputResponse structuredOutput(StructuredOutputRequest request) {
        Map<String, Object> response = postDeepSeek(chatPayload(request.chatRequest(), false, request.schema(), null));
        JsonNode root = objectMapper.valueToTree(response);
        String content = firstChoice(root).path("message").path("content").asText("{}");
        try {
            Map<String, Object> data = objectMapper.readValue(content, MAP_TYPE);
            return new StructuredOutputResponse(data, usage(root), PROVIDER, root.path("model").asText(chatModel(request.chatRequest().model())));
        } catch (JsonProcessingException ex) {
            throw new LlmGatewayException("DeepSeek structured output was not valid JSON", ex);
        }
    }

    @Override
    public ToolCallResponse toolCalling(ToolCallRequest request) {
        Map<String, Object> response = postDeepSeek(chatPayload(request.chatRequest(), false, null, request.tools()));
        JsonNode root = objectMapper.valueToTree(response);
        JsonNode message = firstChoice(root).path("message");
        JsonNode toolCall = message.path("tool_calls").isArray() && !message.path("tool_calls").isEmpty()
                ? message.path("tool_calls").get(0).path("function")
                : null;
        if (toolCall == null || toolCall.isMissingNode()) {
            return new ToolCallResponse(
                    "none",
                    Map.of(),
                    message.path("content").asText(""),
                    usage(root),
                    PROVIDER,
                    root.path("model").asText(chatModel(request.chatRequest().model()))
            );
        }
        return new ToolCallResponse(
                toolCall.path("name").asText("none"),
                parseArguments(toolCall.path("arguments").asText("{}")),
                message.path("content").asText(""),
                usage(root),
                PROVIDER,
                root.path("model").asText(chatModel(request.chatRequest().model()))
        );
    }

    private Map<String, Object> chatPayload(
            ChatRequest request,
            boolean stream,
            Map<String, Object> schema,
            List<ToolCallRequest.ToolDefinition> tools
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel(request.model()));
        body.put("messages", messages(request.messages()));
        body.put("stream", stream);
        body.put("thinking", Map.of("type", "disabled"));

        Map<String, Object> options = request.options() == null ? Map.of() : request.options();
        putOption(body, options, "temperature");
        putOption(body, options, "max_tokens");
        putOption(body, options, "top_p");
        putOption(body, options, "user_id");

        if (schema != null) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(this::toolDefinition).toList());
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private Map<String, Object> postDeepSeek(Map<String, Object> body) {
        try {
            Object rawResponse = deepSeekClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Object.class);
            return objectMapper.convertValue(rawResponse, MAP_TYPE);
        } catch (RestClientException ex) {
            throw new LlmGatewayException("DeepSeek chat request failed", ex);
        }
    }

    private void postDeepSeekStream(Map<String, Object> body, Consumer<String> tokenConsumer) {
        try {
            deepSeekClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .exchange((clientRequest, clientResponse) -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientResponse.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                consumeStreamLine(line, tokenConsumer);
                            }
                        } catch (IOException ex) {
                            throw new LlmGatewayException("Failed to read DeepSeek stream", ex);
                        }
                        return null;
                    });
        } catch (RestClientException ex) {
            throw new LlmGatewayException("DeepSeek stream request failed", ex);
        }
    }

    private void consumeStreamLine(String line, Consumer<String> tokenConsumer) {
        String event = line.strip();
        if (!event.startsWith("data:")) {
            return;
        }
        String data = event.substring(5).strip();
        if ("[DONE]".equals(data) || data.isBlank()) {
            return;
        }
        try {
            JsonNode delta = firstChoice(objectMapper.readTree(data)).path("delta");
            String content = delta.path("content").asText("");
            if (!content.isBlank()) {
                tokenConsumer.accept(content);
            }
        } catch (JsonProcessingException ex) {
            throw new LlmGatewayException("Failed to parse DeepSeek stream chunk", ex);
        }
    }

    private List<Map<String, Object>> messages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(Objects::nonNull)
                .map(message -> Map.<String, Object>of(
                        "role", message.role().name().toLowerCase(Locale.ROOT),
                        "content", defaultIfBlank(message.content(), "")
                ))
                .toList();
    }

    private Map<String, Object> toolDefinition(ToolCallRequest.ToolDefinition tool) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.name(),
                        "description", defaultIfBlank(tool.description(), ""),
                        "parameters", tool.parametersSchema() == null ? Map.of() : tool.parametersSchema()
                )
        );
    }

    private Map<String, Object> parseArguments(String arguments) {
        try {
            return objectMapper.readValue(defaultIfBlank(arguments, "{}"), MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new LlmGatewayException("DeepSeek tool arguments were not valid JSON", ex);
        }
    }

    private JsonNode firstChoice(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmGatewayException("LLM response did not include choices");
        }
        return choices.get(0);
    }

    private TokenUsage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        return new TokenUsage(usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0));
    }

    private TokenUsage embeddingUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        return new TokenUsage(usage.path("prompt_tokens").asInt(0), 0);
    }

    private void putOption(Map<String, Object> body, Map<String, Object> options, String key) {
        if (options.containsKey(key) && options.get(key) != null) {
            body.put(key, options.get(key));
        }
    }

    private String chatModel(String requestModel) {
        return defaultIfBlank(requestModel, defaultIfBlank(properties.deepseek().chatModel(), DEFAULT_CHAT_MODEL));
    }

    private String embeddingModel(String requestModel) {
        return defaultIfBlank(requestModel, defaultIfBlank(properties.dashscope().embeddingModel(), DEFAULT_EMBEDDING_MODEL));
    }

    private String required(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new LlmGatewayException(envName + " is required when lexiflow.llm.provider=deepseek");
        }
        return value;
    }

    private String bearer(String apiKey) {
        return "Bearer " + apiKey;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
