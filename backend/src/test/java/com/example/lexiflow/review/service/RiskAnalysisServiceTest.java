package com.example.lexiflow.review.service;

import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.review.mapper.ClauseRiskMapper;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RiskAnalysisServiceTest {

    @Test
    void detectsUnlimitedLiabilityAsHighApprovalRisk() {
        RiskAnalysisService service = service();
        ContractClause liability = clause("LIABILITY", "Liability", "Party B assumes unlimited liability with no cap.");

        var risks = service.analyze(1L, 2L, List.of(liability), List.of(), user());

        Assertions.assertThat(risks)
                .anySatisfy(risk -> {
                    Assertions.assertThat(risk.getRiskType()).isEqualTo("UNLIMITED_LIABILITY");
                    Assertions.assertThat(risk.getRiskLevel()).isEqualTo("HIGH");
                    Assertions.assertThat(risk.getRequiresApproval()).isTrue();
                });
    }

    @Test
    void detectsLongPaymentTermAsMediumRisk() {
        RiskAnalysisService service = service();
        ContractClause payment = clause("PAYMENT_TERM", "Payment term", "Party A shall pay within 90 days after acceptance.");

        var risks = service.analyze(1L, 2L, List.of(payment), List.of(), user());

        Assertions.assertThat(risks)
                .anySatisfy(risk -> {
                    Assertions.assertThat(risk.getRiskType()).isEqualTo("PAYMENT_TERM_TOO_LONG");
                    Assertions.assertThat(risk.getRiskLevel()).isEqualTo("MEDIUM");
                    Assertions.assertThat(risk.getRequiresApproval()).isFalse();
                });
    }

    @Test
    void detectsHighAmountByConfiguredThreshold() {
        RiskAnalysisService service = service();
        ContractClause amount = clause("AMOUNT", "Amount", "Total contract amount is 120万元.");

        var risks = service.analyze(1L, 2L, List.of(amount), List.of(), user());

        Assertions.assertThat(risks)
                .anySatisfy(risk -> {
                    Assertions.assertThat(risk.getRiskType()).isEqualTo("HIGH_CONTRACT_AMOUNT");
                    Assertions.assertThat(risk.getRiskLevel()).isEqualTo("HIGH");
                    Assertions.assertThat(risk.getRequiresApproval()).isTrue();
                });
    }

    @Test
    void appliesLlmExplanationWhenStructuredOutputIsValid() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any())).thenReturn(new StructuredOutputResponse(
                Map.of(
                        "reason", "LLM refined reason grounded in the payment clause.",
                        "suggestion", "LLM refined suggestion with approval note.",
                        "riskLevel", "MEDIUM",
                        "requiresApproval", false
                ),
                new TokenUsage(10, 8),
                "mock",
                "mock"
        ));
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard(), gateway, 60, 1_000_000);
        ContractClause payment = clause("PAYMENT_TERM", "Payment term", "Payment shall be made within 90 days.");

        var risks = service.analyze(1L, 2L, List.of(payment), List.of(), user());

        Assertions.assertThat(risks)
                .anySatisfy(risk -> {
                    Assertions.assertThat(risk.getReason()).isEqualTo("LLM refined reason grounded in the payment clause.");
                    Assertions.assertThat(risk.getSuggestion()).isEqualTo("LLM refined suggestion with approval note.");
                });
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

    private RiskAnalysisService service() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any()))
                .thenReturn(new StructuredOutputResponse(Map.of(), new TokenUsage(0, 0), "mock", "mock"));
        return new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard(), gateway, 60, 1_000_000);
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
