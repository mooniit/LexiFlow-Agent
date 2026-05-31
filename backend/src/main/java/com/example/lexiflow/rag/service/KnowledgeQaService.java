package com.example.lexiflow.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.llm.mapper.LlmCallLogMapper;
import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.ChatResponse;
import com.example.lexiflow.llm.model.LlmCallLog;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.prompt.service.PromptTemplateService;
import com.example.lexiflow.prompt.service.PromptTemplateService.RenderedPrompt;
import com.example.lexiflow.rag.mapper.QaHistoryMapper;
import com.example.lexiflow.rag.model.QaHistory;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeQaService {

    private final RagRetrievalService retrievalService;
    private final PromptTemplateService promptTemplateService;
    private final LlmGateway llmGateway;
    private final LlmCallLogMapper llmCallLogMapper;
    private final QaHistoryMapper qaHistoryMapper;

    public KnowledgeQaService(RagRetrievalService retrievalService, PromptTemplateService promptTemplateService,
                              LlmGateway llmGateway, LlmCallLogMapper llmCallLogMapper,
                              QaHistoryMapper qaHistoryMapper) {
        this.retrievalService = retrievalService;
        this.promptTemplateService = promptTemplateService;
        this.llmGateway = llmGateway;
        this.llmCallLogMapper = llmCallLogMapper;
        this.qaHistoryMapper = qaHistoryMapper;
    }

    @Transactional
    public QaAnswer ask(QaQuestionRequest request, CurrentUser user) {
        String question = requireQuestion(request.question());
        int limit = request.limit() == null ? 5 : Math.max(1, Math.min(request.limit(), 10));
        List<RetrievedChunk> chunks = retrievalService.retrieve(null, question, limit, request.knowledgeBaseId(), user);
        String answer;
        RenderedPrompt prompt = promptTemplateService.render("KNOWLEDGE_QA", Map.of(
                "question", question,
                "context", context(chunks)
        ));
        if (chunks.isEmpty()) {
            answer = "未在当前可访问的知识库中找到足够依据，无法基于知识库回答该问题。";
        } else {
            answer = generateAnswer(question, prompt, chunks, user);
        }
        QaHistory history = saveHistory(request.knowledgeBaseId(), question, answer, chunks, user);
        return new QaAnswer(history.getId(), question, answer, chunks, history.getCreatedAt());
    }

    public List<QaHistory> history(CurrentUser user) {
        return qaHistoryMapper.selectList(new LambdaQueryWrapper<QaHistory>()
                .eq(QaHistory::getDeleted, false)
                .eq(QaHistory::getUserId, user.id())
                .orderByDesc(QaHistory::getCreatedAt)
                .last("LIMIT 20"));
    }

    @Transactional
    public QaHistory feedback(Long id, FeedbackRequest request, CurrentUser user) {
        QaHistory history = qaHistoryMapper.selectById(id);
        if (history == null || Boolean.TRUE.equals(history.getDeleted()) || !user.id().equals(history.getUserId())) {
            throw new IllegalArgumentException("QA history not found: " + id);
        }
        String feedback = request.feedback();
        if (!"HELPFUL".equals(feedback) && !"NOT_HELPFUL".equals(feedback)) {
            throw new IllegalArgumentException("feedback must be HELPFUL or NOT_HELPFUL.");
        }
        history.setFeedback(feedback);
        history.setUpdatedBy(user.id());
        qaHistoryMapper.updateById(history);
        return history;
    }

    private String generateAnswer(String question, RenderedPrompt prompt, List<RetrievedChunk> chunks, CurrentUser user) {
        ChatRequest chatRequest = new ChatRequest(
                "KNOWLEDGE_QA",
                null,
                List.of(
                        new ChatMessage(ChatMessage.Role.SYSTEM, "只能基于知识库片段回答。"),
                        new ChatMessage(ChatMessage.Role.USER, prompt.content())
                ),
                Map.of("grounded", true)
        );
        Instant start = Instant.now();
        try {
            ChatResponse response = llmGateway.chat(chatRequest);
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            logLlmCall(null, prompt, question, response.content(), response, latencyMs, true, null, user.id());
            if ("mock".equalsIgnoreCase(response.provider())) {
                return groundedAnswer(chunks);
            }
            return response.content();
        } catch (RuntimeException ex) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            logLlmCall(null, prompt, question, null, null, latencyMs, false, ex.getMessage(), user.id());
            return groundedAnswer(chunks);
        }
    }

    private QaHistory saveHistory(Long knowledgeBaseId, String question, String answer,
                                  List<RetrievedChunk> chunks, CurrentUser user) {
        QaHistory history = new QaHistory();
        history.setUserId(user.id());
        history.setKnowledgeBaseId(knowledgeBaseId);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setReferencesJson(referencesJson(chunks));
        history.setRetrievedChunks(chunksJson(chunks));
        history.setCreatedBy(user.id());
        history.setUpdatedBy(user.id());
        qaHistoryMapper.insert(history);
        return history;
    }

    private void logLlmCall(Long reviewId, RenderedPrompt prompt, String question, String answer,
                            ChatResponse response, long latencyMs, boolean success, String errorMessage, Long userId) {
        TokenUsage usage = response == null ? null : response.usage();
        LlmCallLog log = new LlmCallLog();
        log.setReviewId(reviewId);
        log.setProvider(response == null ? null : response.provider());
        log.setModelName(response == null ? null : response.model());
        log.setPromptVersion(prompt.version());
        log.setRequestBody("{\"scenario\":\"KNOWLEDGE_QA\",\"promptName\":" + JsonStrings.quote(prompt.name())
                + ",\"question\":" + JsonStrings.quote(question) + "}");
        log.setResponseBody("{\"answer\":" + JsonStrings.quote(answer) + "}");
        log.setPromptTokens(usage == null ? null : usage.promptTokens());
        log.setCompletionTokens(usage == null ? null : usage.completionTokens());
        log.setTotalTokens(usage == null ? null : usage.totalTokens());
        log.setLatencyMs(latencyMs);
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setCreatedBy(userId);
        llmCallLogMapper.insert(log);
    }

    private String groundedAnswer(List<RetrievedChunk> chunks) {
        StringBuilder answer = new StringBuilder("根据知识库，相关规则可归纳如下：");
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            answer.append("\n").append(i + 1).append(". ").append(firstLine(chunks.get(i).content()));
        }
        answer.append("\n\n以上结论仅基于本次检索到的知识库片段。");
        return answer.toString();
    }

    private String firstLine(String content) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "..." : compact;
    }

    private String context(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> "[文档#" + chunk.documentId() + " chunk#" + chunk.chunkId() + " score=" + chunk.score()
                        + "]\n" + chunk.content())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String referencesJson(List<RetrievedChunk> chunks) {
        return "[" + chunks.stream()
                .map(chunk -> "{\"chunkId\":" + chunk.chunkId()
                        + ",\"documentId\":" + chunk.documentId()
                        + ",\"score\":" + chunk.score() + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String chunksJson(List<RetrievedChunk> chunks) {
        return "[" + chunks.stream()
                .map(chunk -> "{\"chunkId\":" + chunk.chunkId()
                        + ",\"documentId\":" + chunk.documentId()
                        + ",\"score\":" + chunk.score()
                        + ",\"content\":" + JsonStrings.quote(chunk.content()) + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question is required.");
        }
        return question.trim();
    }

    public record QaQuestionRequest(String question, Long knowledgeBaseId, Integer limit) {
    }

    public record FeedbackRequest(String feedback) {
    }

    public record QaAnswer(Long id, String question, String answer, List<RetrievedChunk> references,
                           java.time.OffsetDateTime createdAt) {
    }
}
