package com.example.lexiflow.review.service;

import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.review.mapper.ClauseRiskMapper;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RiskAnalysisServiceTest {

    @Test
    void detectsUnlimitedLiabilityAsHighApprovalRisk() {
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard());
        ContractClause liability = clause("LIABILITY", "违约责任", "乙方承担无限赔偿责任，不设上限。");

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
        RiskAnalysisService service = new RiskAnalysisService(Mockito.mock(ClauseRiskMapper.class), toolGuard());
        ContractClause payment = clause("PAYMENT_TERM", "付款周期", "甲方应在验收后 90 天内付款。");

        var risks = service.analyze(1L, 2L, List.of(payment), List.of(), user());

        Assertions.assertThat(risks)
                .anySatisfy(risk -> {
                    Assertions.assertThat(risk.getRiskType()).isEqualTo("PAYMENT_TERM_TOO_LONG");
                    Assertions.assertThat(risk.getRiskLevel()).isEqualTo("MEDIUM");
                    Assertions.assertThat(risk.getRequiresApproval()).isFalse();
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
