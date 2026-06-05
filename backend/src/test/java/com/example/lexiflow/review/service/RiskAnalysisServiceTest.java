package com.example.lexiflow.review.service;

import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
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

    @Test
    void includesClauseInsightFactsAndSignalsInLlmRiskPrompt() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any())).thenReturn(new StructuredOutputResponse(
                Map.of(),
                new TokenUsage(10, 8),
                "mock",
                "mock"
        ));
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard(), gateway, 60, 1_000_000);
        ContractClause payment = clause("PAYMENT_TERM", "Payment term", "Payment shall be made within 90 days.");
        List<ContractReviewService.ClauseInsight> insights = List.of(new ContractReviewService.ClauseInsight(
                "Payment term",
                "PAYMENT_TERM",
                "付款条款",
                "Payment shall be made within 90 days.",
                List.of("paymentDays=90"),
                List.of("付款周期超过60日"),
                "Payment shall be made within 90 days."
        ));

        service.analyze(1L, 2L, List.of(payment), List.of(), insights, user());

        var captor = org.mockito.ArgumentCaptor.forClass(StructuredOutputRequest.class);
        Mockito.verify(gateway, Mockito.atLeastOnce()).structuredOutput(captor.capture());
        Assertions.assertThat(captor.getAllValues())
                .anySatisfy(request -> Assertions.assertThat(request.chatRequest().messages().getLast().content())
                        .contains("Clause insight")
                        .contains("paymentDays=90")
                        .contains("付款周期超过60日"));
    }

    @Test
    void includesLlmDiscoveredHighRiskInAnalysisResults() {
        RiskAnalysisService service = service();
        ContractClause liability = clause("LIABILITY", "第九条 违约责任", "甲方责任不设上限，乙方责任以已付款5%为上限。");
        RiskDiscoveryService.DiscoveredRisk discovered = new RiskDiscoveryService.DiscoveredRisk(
                "UNBALANCED_LIABILITY",
                "HIGH",
                "第九条 违约责任",
                "甲方责任不设上限，乙方责任以已付款5%为上限。",
                "责任分配严重不对等。",
                "建议设置对等责任上限。",
                List.of("责任上限规则"),
                0.91,
                true
        );

        var risks = service.analyze(1L, 2L, List.of(liability), List.of(), List.of(), List.of(discovered), user());

        Assertions.assertThat(risks)
                .anySatisfy(risk -> {
                    Assertions.assertThat(risk.getRiskType()).isEqualTo("UNBALANCED_LIABILITY");
                    Assertions.assertThat(risk.getRiskLevel()).isEqualTo("HIGH");
                    Assertions.assertThat(risk.getRequiresApproval()).isTrue();
                    Assertions.assertThat(risk.getReason()).contains("责任分配严重不对等");
                });
    }

    @Test
    void doesNotAllowLlmEnhancementToDowngradeHighRisks() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any())).thenReturn(new StructuredOutputResponse(
                Map.of(
                        "reason", "LLM tried to soften this risk.",
                        "suggestion", "Keep watching.",
                        "riskLevel", "MEDIUM",
                        "requiresApproval", false
                ),
                new TokenUsage(10, 8),
                "mock",
                "mock"
        ));
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard(), gateway, 60, 1_000_000);
        ContractClause liability = clause("LIABILITY", "第九条 违约责任", "Party B assumes unlimited liability with no cap.");

        var risks = service.analyze(1L, 2L, List.of(liability), List.of(), user());

        Assertions.assertThat(risks)
                .anySatisfy(risk -> {
                    Assertions.assertThat(risk.getRiskType()).isEqualTo("UNLIMITED_LIABILITY");
                    Assertions.assertThat(risk.getRiskLevel()).isEqualTo("HIGH");
                    Assertions.assertThat(risk.getRequiresApproval()).isTrue();
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
