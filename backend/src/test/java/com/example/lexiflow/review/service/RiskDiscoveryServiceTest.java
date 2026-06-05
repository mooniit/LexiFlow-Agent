package com.example.lexiflow.review.service;

import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.llm.mapper.LlmCallLogMapper;
import com.example.lexiflow.llm.model.LlmCallLog;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RiskDiscoveryServiceTest {

    @Test
    void parsesLlmDiscoveredRisksAndLogsTheCall() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any())).thenReturn(new StructuredOutputResponse(
                Map.of("risks", List.of(Map.of(
                        "riskType", "UNBALANCED_LIABILITY",
                        "riskLevel", "HIGH",
                        "clauseName", "第九条 违约责任",
                        "clauseTextEvidence", "甲方责任不设上限，乙方责任以已付款5%为上限。",
                        "reason", "责任分配严重不对等。",
                        "suggestion", "建议设置对等责任上限。",
                        "references", List.of("责任上限规则"),
                        "confidence", 0.91,
                        "requiresApproval", true
                ))),
                new TokenUsage(40, 20),
                "mock",
                "mock-risk-discovery"
        ));
        LlmCallLogMapper logMapper = Mockito.mock(LlmCallLogMapper.class);
        RiskDiscoveryService service = new RiskDiscoveryService(gateway, logMapper);

        List<RiskDiscoveryService.DiscoveredRisk> risks = service.discover(
                99L,
                contract(),
                List.of(new ContractReviewService.ClauseInsight(
                        "第九条 违约责任",
                        "LIABILITY",
                        "违约责任",
                        "甲方责任不设上限，乙方责任以已付款5%为上限。",
                        List.of("甲方责任不设上限"),
                        List.of("双方责任明显不对等"),
                        "甲方责任不设上限，乙方责任以已付款5%为上限."
                )),
                List.of(new RetrievedChunk(1L, 2L, "责任上限规则", 0.88)),
                user()
        );

        Assertions.assertThat(risks).singleElement().satisfies(risk -> {
            Assertions.assertThat(risk.riskType()).isEqualTo("UNBALANCED_LIABILITY");
            Assertions.assertThat(risk.riskLevel()).isEqualTo("HIGH");
            Assertions.assertThat(risk.requiresApproval()).isTrue();
            Assertions.assertThat(risk.references()).contains("责任上限规则");
        });
        var logCaptor = org.mockito.ArgumentCaptor.forClass(LlmCallLog.class);
        Mockito.verify(logMapper).insert(logCaptor.capture());
        Assertions.assertThat(logCaptor.getValue().getPromptVersion()).isEqualTo("risk-discovery");
        Assertions.assertThat(logCaptor.getValue().getSuccess()).isTrue();
    }

    private Contract contract() {
        Contract contract = new Contract();
        contract.setContractType("采购合同");
        contract.setContractAmount(new BigDecimal("3680000"));
        contract.setCustomerName("上海启明智能制造有限公司");
        return contract;
    }

    private CurrentUser user() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("tool:execute"), true);
    }
}
