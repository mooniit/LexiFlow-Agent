package com.example.lexiflow.llm.service;

import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.review.mapper.ClauseRiskMapper;
import com.example.lexiflow.review.service.RiskAnalysisService;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LlmJsonParsingTest {

    private final MockLlmGateway mockGateway = new MockLlmGateway();

    // === structured output JSON parsing ===

    @Test
    void structuredOutputReturnsParsableEnvelope() {
        ChatRequest chatRequest = new ChatRequest(
                "clause-extraction",
                null,
                List.of(new ChatMessage(ChatMessage.Role.USER, "Extract: Party A shall pay within 30 days.")),
                Map.of()
        );

        var result = mockGateway.structuredOutput(new StructuredOutputRequest(chatRequest, Map.of("type", "object")));

        Assertions.assertThat(result.data()).containsEntry("scenario", "clause-extraction");
        Assertions.assertThat(result.data()).containsEntry("mock", true);
        Assertions.assertThat(result.data()).containsKey("summary");
    }

    @Test
    void structuredOutputPreservesScenarioFromChatRequest() {
        for (String scenario : List.of("clause-extraction", "risk-analysis", "report-generation")) {
            ChatRequest chatRequest = new ChatRequest(scenario, null, List.of(userMsg("test")), Map.of());
            var result = mockGateway.structuredOutput(new StructuredOutputRequest(chatRequest, Map.of()));

            Assertions.assertThat(result.data()).containsEntry("scenario", scenario);
        }
    }

    @Test
    void structuredOutputWithEmptyMessagesReturnsDeterministicResult() {
        ChatRequest chatRequest = new ChatRequest("empty-test", null, List.of(), Map.of());

        Assertions.assertThatCode(() -> mockGateway.structuredOutput(new StructuredOutputRequest(chatRequest, Map.of())))
                .doesNotThrowAnyException();
    }

    // === LLM failure graceful degradation ===

    @Test
    void riskAnalysisDegradesGracefullyWhenLlmFails() {
        LlmGateway failingGateway = Mockito.mock(LlmGateway.class);
        Mockito.when(failingGateway.structuredOutput(Mockito.any())).thenThrow(new LlmGatewayException("LLM timeout"));
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard(), failingGateway, 60, 1_000_000);
        ContractClause liability = clause("LIABILITY", "Liability", "Unlimited liability.");

        List<?> risks = service.analyze(1L, 2L, List.of(liability), List.of(), user());

        Assertions.assertThat(risks).isNotEmpty();
    }

    @Test
    void riskAnalysisDegradesGracefullyWhenLlmReturnsInvalidJson() {
        LlmGateway invalidGateway = Mockito.mock(LlmGateway.class);
        Mockito.when(invalidGateway.structuredOutput(Mockito.any())).thenReturn(new StructuredOutputResponse(
                Map.of("unexpected", "field"),
                new TokenUsage(1, 1),
                "mock",
                "mock"
        ));
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard(), invalidGateway, 60, 1_000_000);
        ContractClause payment = clause("PAYMENT_TERM", "Payment", "Payment within 90 days.");

        List<?> risks = service.analyze(1L, 2L, List.of(payment), List.of(), user());

        Assertions.assertThat(risks).isNotEmpty();
        Assertions.assertThat(risks).anySatisfy(risk -> {
            var r = (com.example.lexiflow.review.model.ClauseRisk) risk;
            Assertions.assertThat(r.getRiskType()).isEqualTo("PAYMENT_TERM_TOO_LONG");
        });
    }

    // === helpers ===

    private ChatMessage userMsg(String content) {
        return new ChatMessage(ChatMessage.Role.USER, content);
    }

    private ContractClause clause(String type, String name, String text) {
        ContractClause clause = new ContractClause();
        clause.setId(10L);
        clause.setClauseType(type);
        clause.setClauseName(name);
        clause.setClauseText(text);
        return clause;
    }

    private CurrentUser user() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("tool:execute"), true);
    }

    private ToolPermissionGuard toolGuard() {
        ReviewToolConfigMapper mapper = Mockito.mock(ReviewToolConfigMapper.class);
        ReviewToolConfig config = new ReviewToolConfig();
        config.setToolName("risk_analysis");
        config.setRequiredPermission("tool:execute");
        config.setEnabled(true);
        config.setApprovalRequired(false);
        Mockito.when(mapper.selectOne(Mockito.any())).thenReturn(config);
        return new ToolPermissionGuard(mapper);
    }
}
