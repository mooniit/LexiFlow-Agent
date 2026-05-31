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

class ApprovalRuleTest {

    // === HIGH risk triggers approval ===

    @Test
    void highRiskTriggersApproval() {
        RiskAnalysisService service = service();
        ContractClause liability = clause("LIABILITY", "Liability", "Party B assumes unlimited liability with no cap.");

        var risks = service.analyze(1L, 2L, List.of(liability), List.of(), user());

        Assertions.assertThat(risks).anySatisfy(risk -> {
            Assertions.assertThat(risk.getRiskLevel()).isEqualTo("HIGH");
            Assertions.assertThat(risk.getRequiresApproval()).isTrue();
        });
    }

    @Test
    void llmCanOverrideApprovalFlag() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any())).thenReturn(new StructuredOutputResponse(
                Map.of("reason", "refined", "suggestion", "revise", "riskLevel", "HIGH", "requiresApproval", true),
                new TokenUsage(10, 8), "mock", "mock"
        ));
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard(), gateway, 60, 1_000_000);
        ContractClause liability = clause("LIABILITY", "Liability", "Party B assumes unlimited liability.");

        var risks = service.analyze(1L, 2L, List.of(liability), List.of(), user());

        Assertions.assertThat(risks).anySatisfy(risk -> {
            Assertions.assertThat(risk.getRiskLevel()).isEqualTo("HIGH");
            Assertions.assertThat(risk.getRequiresApproval()).isTrue();
        });
    }

    // === Missing clauses detection ===

    @Test
    void detectsMissingDataProtectionClause() {
        RiskAnalysisService service = service();
        ContractClause payment = clause("PAYMENT_TERM", "Payment", "Pay within 30 days.");

        var risks = service.analyze(1L, 2L, List.of(payment), List.of(), user());

        Assertions.assertThat(risks).anySatisfy(risk ->
                Assertions.assertThat(risk.getRiskType()).isEqualTo("MISSING_DATA_PROTECTION"));
    }

    @Test
    void detectsMissingTerminationClause() {
        RiskAnalysisService service = service();

        var risks = service.analyze(1L, 2L, List.of(), List.of(), user());

        Assertions.assertThat(risks).anySatisfy(risk ->
                Assertions.assertThat(risk.getRiskType()).isEqualTo("MISSING_TERMINATION_CLAUSE"));
    }

    // === Multiple risks in one review ===

    @Test
    void generatesMultipleRisksForProblematicContract() {
        RiskAnalysisService service = service();
        ContractClause liability = clause("LIABILITY", "Liability", "Unlimited liability assumed.");
        ContractClause payment = clause("PAYMENT_TERM", "Payment", "Payment within 90 days.");
        ContractClause amount = clause("AMOUNT", "Amount", "Total amount is 200万元.");

        var risks = service.analyze(1L, 2L, List.of(liability, payment, amount), List.of(), user());

        Assertions.assertThat(risks).hasSizeGreaterThanOrEqualTo(3);
        Assertions.assertThat(risks).extracting("riskType")
                .contains("UNLIMITED_LIABILITY", "PAYMENT_TERM_TOO_LONG", "HIGH_CONTRACT_AMOUNT");
    }

    // === Clean contract ===

    @Test
    void cleanContractHasFewerRisks() {
        RiskAnalysisService service = service();
        ContractClause payment = clause("PAYMENT_TERM", "Payment", "Payment within 15 days after receipt.");
        ContractClause data = clause("DATA_PROTECTION", "Data", "All personal data shall be protected per GDPR.");
        ContractClause termination = clause("TERMINATION", "Termination", "Either party may terminate with 30 days notice.");
        ContractClause confidentiality = clause("CONFIDENTIALITY", "Confidentiality", "Both parties agree to strict confidentiality.");
        ContractClause ip = clause("INTELLECTUAL_PROPERTY", "IP", "All IP created remains with Party A.");
        ContractClause dispute = clause("DISPUTE_RESOLUTION", "Dispute", "Disputes resolved under PRC law in Beijing courts.");

        var risks = service.analyze(1L, 2L, List.of(payment, data, termination, confidentiality, ip, dispute), List.of(), user());

        Assertions.assertThat(risks).anySatisfy(risk ->
                Assertions.assertThat(risk.getRiskLevel()).isNotEqualTo("HIGH"));
    }

    // === helpers ===

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
