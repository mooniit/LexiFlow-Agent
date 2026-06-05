package com.example.lexiflow.review.service;

import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.llm.mapper.LlmCallLogMapper;
import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.LlmCallLog;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RiskDiscoveryService {

    private final LlmGateway llmGateway;
    private final LlmCallLogMapper llmCallLogMapper;

    public RiskDiscoveryService(LlmGateway llmGateway, LlmCallLogMapper llmCallLogMapper) {
        this.llmGateway = llmGateway;
        this.llmCallLogMapper = llmCallLogMapper;
    }

    public List<DiscoveredRisk> discover(Long reviewId, Contract contract,
                                         List<ContractReviewService.ClauseInsight> insights,
                                         List<RetrievedChunk> references,
                                         CurrentUser user) {
        Instant start = Instant.now();
        String prompt = prompt(contract, insights, references);
        try {
            StructuredOutputResponse response = llmGateway.structuredOutput(new StructuredOutputRequest(
                    new ChatRequest("risk-discovery", null, List.of(
                            new ChatMessage(ChatMessage.Role.SYSTEM,
                                    "你是合同风险发现 Agent。只基于条款 insight 和 RAG 引用输出风险候选 JSON，不要编造事实。"),
                            new ChatMessage(ChatMessage.Role.USER, prompt)
                    ), Map.of("temperature", 0)),
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "risks", Map.of("type", "array", "items", Map.of("type", "object"))
                            )
                    )
            ));
            List<DiscoveredRisk> risks = parseRisks(response.data());
            log(reviewId, prompt, response, Duration.between(start, Instant.now()).toMillis(), true, null, user.id());
            return risks;
        } catch (RuntimeException ex) {
            log(reviewId, prompt, null, Duration.between(start, Instant.now()).toMillis(), false, ex.getMessage(), user.id());
            return List.of();
        }
    }

    private String prompt(Contract contract, List<ContractReviewService.ClauseInsight> insights,
                          List<RetrievedChunk> references) {
        StringBuilder builder = new StringBuilder("请发现合同风险，输出 {\"risks\":[...]}。\n");
        builder.append("合同类型: ").append(safe(contract.getContractType())).append("\n");
        builder.append("合同金额: ").append(contract.getContractAmount() == null ? "" : contract.getContractAmount()).append("\n");
        builder.append("客户: ").append(safe(contract.getCustomerName())).append("\n\n");
        builder.append("条款 insight:\n");
        for (ContractReviewService.ClauseInsight insight : insights) {
            builder.append("- clauseName=").append(insight.clauseName())
                    .append(", clauseType=").append(insight.clauseType())
                    .append(", label=").append(insight.clauseTypeLabel())
                    .append(", suggestedRiskLevel=").append(insight.suggestedRiskLevel())
                    .append(", confidence=").append(insight.confidence())
                    .append(", summary=").append(insight.summary())
                    .append(", facts=").append(insight.keyFacts())
                    .append(", signals=").append(insight.riskSignals())
                    .append(", evidence=").append(insight.evidence())
                    .append("\n");
        }
        builder.append("\nRAG 引用:\n");
        references.stream().limit(8).forEach(ref -> builder.append("- ").append(ref.content()).append("\n"));
        builder.append("""

                每个 risk 字段:
                riskType, riskLevel(LOW/MEDIUM/HIGH), clauseName, clauseTextEvidence,
                reason, suggestion, references(array), confidence(0-1), requiresApproval(boolean).
                高风险示例: 无限责任、责任严重不对等、单方调价/变更、重大金额、数据处理缺失、自动续约且费用显著上浮。
                """);
        return builder.toString();
    }

    private List<DiscoveredRisk> parseRisks(Map<String, Object> data) {
        if (data == null || !(data.get("risks") instanceof List<?> values)) {
            return List.of();
        }
        List<DiscoveredRisk> risks = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                risks.add(new DiscoveredRisk(
                        string(map.get("riskType")),
                        normalizeRiskLevel(string(map.get("riskLevel"))),
                        string(map.get("clauseName")),
                        string(map.get("clauseTextEvidence")),
                        string(map.get("reason")),
                        string(map.get("suggestion")),
                        strings(map.get("references")),
                        number(map.get("confidence")),
                        Boolean.TRUE.equals(map.get("requiresApproval")) || "HIGH".equals(normalizeRiskLevel(string(map.get("riskLevel"))))
                ));
            }
        }
        return risks.stream().filter(risk -> !risk.riskType().isBlank()).toList();
    }

    private void log(Long reviewId, String prompt, StructuredOutputResponse response, long latencyMs,
                     boolean success, String errorMessage, Long userId) {
        if (llmCallLogMapper == null) {
            return;
        }
        TokenUsage usage = response == null ? null : response.usage();
        LlmCallLog log = new LlmCallLog();
        log.setReviewId(reviewId);
        log.setProvider(response == null ? null : response.provider());
        log.setModelName(response == null ? null : response.model());
        log.setPromptVersion("risk-discovery");
        log.setRequestBody("{\"scenario\":\"risk-discovery\",\"prompt\":" + JsonStrings.quote(prompt) + "}");
        log.setResponseBody("{\"data\":" + JsonStrings.quote(response == null ? null : String.valueOf(response.data())) + "}");
        log.setPromptTokens(usage == null ? null : usage.promptTokens());
        log.setCompletionTokens(usage == null ? null : usage.completionTokens());
        log.setTotalTokens(usage == null ? null : usage.totalTokens());
        log.setLatencyMs(latencyMs);
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setCreatedBy(userId);
        llmCallLogMapper.insert(log);
    }

    private static String normalizeRiskLevel(String value) {
        return List.of("LOW", "MEDIUM", "HIGH").contains(value) ? value : "MEDIUM";
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(string(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record DiscoveredRisk(String riskType, String riskLevel, String clauseName, String clauseTextEvidence,
                                 String reason, String suggestion, List<String> references,
                                 double confidence, boolean requiresApproval) {
    }
}
