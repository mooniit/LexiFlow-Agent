package com.example.lexiflow.review.service;

import com.example.lexiflow.approval.service.ApprovalService;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.contract.service.ClauseExtractionService;
import com.example.lexiflow.contract.service.ContractService;
import com.example.lexiflow.llm.mapper.LlmCallLogMapper;
import com.example.lexiflow.llm.model.LlmCallLog;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.llm.service.LlmGatewayException;
import com.example.lexiflow.rag.model.RetrievalLog;
import com.example.lexiflow.rag.mapper.RetrievalLogMapper;
import com.example.lexiflow.rag.service.RagRetrievalService;
import com.example.lexiflow.review.mapper.AgentStateTransitionLogMapper;
import com.example.lexiflow.review.mapper.AgentStepMapper;
import com.example.lexiflow.review.mapper.ContractReviewMapper;
import com.example.lexiflow.review.model.AgentStep;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.review.model.ContractReview;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ToolCallLogMapper;
import com.example.lexiflow.tool.model.ToolCallLog;
import com.example.lexiflow.user.mapper.AppUserMapper;
import com.example.lexiflow.user.mapper.UserPermissionMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ContractReviewServiceTest {

    @Test
    void buildsFocusedRetrievalQueryFromClauseTypesInsteadOfLongContractSnippets() {
        Contract contract = new Contract();
        contract.setContractType("SALES");
        contract.setContractAmount(new BigDecimal("1256000"));
        contract.setCustomerName("北京宏远商贸有限公司");

        ContractClause parties = clause("PARTIES", "合同主体",
                "合同编号：XS-2025-001\n2025年度销售合同\n甲方（卖方）：深圳市星辰科技有限公司\n乙方（买方）：北京宏远商贸有限公司\n第一条 合同标的...");
        ContractClause payment = clause("PAYMENT_TERM", "付款周期", "乙方应在验收后90天内支付剩余款项。");
        ContractClause liability = clause("LIABILITY", "违约责任", "违约方应承担赔偿责任。");

        String query = ContractReviewService.buildRetrievalQuery(contract, List.of(parties, payment, liability));

        Assertions.assertThat(query)
                .contains("合同审查规则检索")
                .contains("合同类型: SALES")
                .contains("合同金额: 1256000")
                .contains("PAYMENT_TERM: 付款周期")
                .contains("LIABILITY: 违约责任")
                .contains("付款周期限制")
                .contains("违约责任");
        Assertions.assertThat(query)
                .doesNotContain("合同编号：XS-2025-001")
                .doesNotContain("甲方（卖方）");
    }

    @Test
    void buildsClauseInsightsWithChineseLabelsFactsAndRiskSignals() {
        ContractClause payment = clause("PAYMENT_TERM", "第二条 付款方式", "乙方应在验收后90日内支付剩余款项。");
        ContractClause liability = clause("LIABILITY", "第四条 违约责任", "违约方承担赔偿责任，责任不设上限。");

        List<ContractReviewService.ClauseInsight> insights =
                ContractReviewService.buildClauseInsights(List.of(payment, liability));

        Assertions.assertThat(insights)
                .extracting(ContractReviewService.ClauseInsight::clauseTypeLabel)
                .containsExactly("付款条款", "违约责任");
        Assertions.assertThat(insights.get(0).keyFacts()).contains("paymentDays=90");
        Assertions.assertThat(insights.get(1).riskSignals()).contains("可能存在无限责任或责任上限缺失");
    }

    @Test
    void groupsRelatedClauseInsightsIntoThemeRetrievalQueries() {
        Contract contract = new Contract();
        contract.setContractType("SALES");
        contract.setContractAmount(new BigDecimal("1256000"));
        contract.setCustomerName("北京宏远商贸有限公司");

        ContractClause amount = clause("AMOUNT", "第一条 合同金额", "合同总金额为1256000元。");
        ContractClause payment = clause("PAYMENT_TERM", "第二条 付款方式", "乙方应在验收后90日内付款。");
        ContractClause liability = clause("LIABILITY", "第四条 违约责任", "违约方承担赔偿责任，责任不设上限。");

        List<ContractReviewService.ThemeRetrievalQuery> queries =
                ContractReviewService.buildThemeRetrievalQueries(
                        contract,
                        ContractReviewService.buildClauseInsights(List.of(amount, payment, liability)));

        Assertions.assertThat(queries)
                .extracting(ContractReviewService.ThemeRetrievalQuery::theme)
                .containsExactly("付款与金额", "责任与违约");
        Assertions.assertThat(queries.get(0).query())
                .contains("付款与金额")
                .contains("合同金额")
                .contains("付款条款")
                .contains("paymentDays=90")
                .doesNotContain("违约方承担赔偿责任");
        Assertions.assertThat(queries.get(1).query())
                .contains("责任与违约")
                .contains("可能存在无限责任或责任上限缺失");
    }

    @Test
    void usesLlmStructuredOutputForClauseInsightsAndLogsCall() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any())).thenReturn(new StructuredOutputResponse(
                Map.of(
                        "summary", "LLM summary: 90-day payment after acceptance.",
                        "keyFacts", List.of("paymentDays=90", "trigger=acceptance"),
                        "riskSignals", List.of("付款周期超过60日", "缺少逾期利息约定")
                ),
                new TokenUsage(30, 20),
                "mock",
                "mock-insight"
        ));
        LlmCallLogMapper logMapper = Mockito.mock(LlmCallLogMapper.class);
        ContractReviewService service = service(gateway, logMapper);
        ContractClause payment = clause("PAYMENT_TERM", "第二条 付款方式", "乙方应在验收后90日内支付剩余款项。");

        List<ContractReviewService.ClauseInsight> insights = service.buildClauseInsights(99L, List.of(payment), user());

        Assertions.assertThat(insights).singleElement().satisfies(insight -> {
            Assertions.assertThat(insight.summary()).isEqualTo("LLM summary: 90-day payment after acceptance.");
            Assertions.assertThat(insight.keyFacts()).containsExactly("paymentDays=90", "trigger=acceptance");
            Assertions.assertThat(insight.riskSignals()).containsExactly("付款周期超过60日", "缺少逾期利息约定");
        });
        var logCaptor = org.mockito.ArgumentCaptor.forClass(LlmCallLog.class);
        Mockito.verify(logMapper).insert(logCaptor.capture());
        Assertions.assertThat(logCaptor.getValue().getPromptVersion()).isEqualTo("clause-insight");
        Assertions.assertThat(logCaptor.getValue().getSuccess()).isTrue();
        Assertions.assertThat(logCaptor.getValue().getRequestBody()).contains("PAYMENT_TERM");
    }

    @Test
    void fallsBackToDeterministicClauseInsightWhenLlmExtractionFails() {
        LlmGateway gateway = Mockito.mock(LlmGateway.class);
        Mockito.when(gateway.structuredOutput(Mockito.any())).thenThrow(new LlmGatewayException("LLM timeout"));
        LlmCallLogMapper logMapper = Mockito.mock(LlmCallLogMapper.class);
        ContractReviewService service = service(gateway, logMapper);
        ContractClause payment = clause("PAYMENT_TERM", "第二条 付款方式", "乙方应在验收后90日内支付剩余款项。");

        List<ContractReviewService.ClauseInsight> insights = service.buildClauseInsights(99L, List.of(payment), user());

        Assertions.assertThat(insights).singleElement().satisfies(insight -> {
            Assertions.assertThat(insight.keyFacts()).contains("paymentDays=90");
            Assertions.assertThat(insight.riskSignals()).contains("付款周期超过60日");
        });
        var logCaptor = org.mockito.ArgumentCaptor.forClass(LlmCallLog.class);
        Mockito.verify(logMapper).insert(logCaptor.capture());
        Assertions.assertThat(logCaptor.getValue().getPromptVersion()).isEqualTo("clause-insight");
        Assertions.assertThat(logCaptor.getValue().getSuccess()).isFalse();
        Assertions.assertThat(logCaptor.getValue().getErrorMessage()).contains("LLM timeout");
    }

    @Test
    void traceMetricsSeparateWallClockStepToolLlmAndRetrievalLatency() {
        OffsetDateTime base = OffsetDateTime.now();
        ContractReview review = new ContractReview();
        review.setId(99L);
        review.setStatus("COMPLETED");

        ContractReviewMapper reviewMapper = Mockito.mock(ContractReviewMapper.class);
        Mockito.when(reviewMapper.selectById(99L)).thenReturn(review);

        AgentStepMapper stepMapper = Mockito.mock(AgentStepMapper.class);
        Mockito.when(stepMapper.selectList(Mockito.any())).thenReturn(List.of(
                step("CONTRACT_PARSE", base, base.plus(Duration.ofMillis(10))),
                step("RISK_ANALYSIS", base.plus(Duration.ofMillis(20)), base.plus(Duration.ofMillis(55)))
        ));

        LlmCallLogMapper llmMapper = Mockito.mock(LlmCallLogMapper.class);
        Mockito.when(llmMapper.selectList(Mockito.any())).thenReturn(List.of(
                llm(10, 12L),
                llm(5, 8L)
        ));

        ToolCallLogMapper toolMapper = Mockito.mock(ToolCallLogMapper.class);
        Mockito.when(toolMapper.selectList(Mockito.any())).thenReturn(List.of(tool(30L)));

        RetrievalLogMapper retrievalMapper = Mockito.mock(RetrievalLogMapper.class);
        Mockito.when(retrievalMapper.selectList(Mockito.any())).thenReturn(List.of(retrieval(4L)));

        ContractReviewService service = service(
                reviewMapper,
                stepMapper,
                Mockito.mock(AgentStateTransitionLogMapper.class),
                llmMapper,
                toolMapper,
                retrievalMapper
        );

        ContractReviewService.ReviewTrace trace = service.trace(99L, traceUser());

        Assertions.assertThat(trace.metrics().stepDurationMs()).isEqualTo(45);
        Assertions.assertThat(trace.metrics().wallClockMs()).isEqualTo(55);
        Assertions.assertThat(trace.metrics().toolLatencyMs()).isEqualTo(30);
        Assertions.assertThat(trace.metrics().llmLatencyMs()).isEqualTo(20);
        Assertions.assertThat(trace.metrics().retrievalLatencyMs()).isEqualTo(4);
        Assertions.assertThat(trace.metrics().totalTokens()).isEqualTo(15);
    }

    @Test
    void resumeAfterApprovalPersistsReportSummaryThroughProgressRecorder() {
        ContractReview waiting = review(99L, 2L, "WAITING_APPROVAL");
        ContractReview generating = review(99L, 2L, "GENERATING_REPORT");
        ContractReview completed = review(99L, 2L, "COMPLETED");

        ContractReviewMapper reviewMapper = Mockito.mock(ContractReviewMapper.class);
        Mockito.when(reviewMapper.selectById(99L)).thenReturn(waiting, completed);

        ClauseExtractionService clauseExtractionService = Mockito.mock(ClauseExtractionService.class);
        Mockito.when(clauseExtractionService.listByContract(2L)).thenReturn(List.of(clause("LIABILITY", "违约责任", "责任不设上限。")));

        RiskAnalysisService riskAnalysisService = Mockito.mock(RiskAnalysisService.class);
        Mockito.when(riskAnalysisService.listByReview(99L)).thenReturn(List.of(highRisk()));

        ReviewProgressRecorder progressRecorder = Mockito.mock(ReviewProgressRecorder.class);
        AgentStep reportStep = new AgentStep();
        reportStep.setId(7L);
        Mockito.when(progressRecorder.transition(Mockito.eq(99L), Mockito.any(), Mockito.anyString(), Mockito.eq(1L), Mockito.anyInt()))
                .thenReturn(generating, completed);
        Mockito.when(progressRecorder.beginStep(Mockito.eq(99L), Mockito.any(), Mockito.anyString(), Mockito.eq(1L)))
                .thenReturn(reportStep);
        Mockito.when(progressRecorder.updateReviewResult(Mockito.eq(99L), Mockito.eq("HIGH"), Mockito.contains("Manual approval passed"), Mockito.eq(1L)))
                .thenReturn(generating);

        ContractReviewService service = service(
                reviewMapper,
                Mockito.mock(AgentStepMapper.class),
                Mockito.mock(AgentStateTransitionLogMapper.class),
                Mockito.mock(LlmCallLogMapper.class),
                Mockito.mock(ToolCallLogMapper.class),
                Mockito.mock(RetrievalLogMapper.class),
                clauseExtractionService,
                riskAnalysisService,
                progressRecorder
        );

        ContractReview result = service.resumeAfterApproval(99L, traceUser());

        Assertions.assertThat(result.getStatus()).isEqualTo("COMPLETED");
        Mockito.verify(progressRecorder).updateReviewResult(Mockito.eq(99L), Mockito.eq("HIGH"),
                Mockito.contains("Manual approval passed"), Mockito.eq(1L));
        Mockito.verify(reviewMapper, Mockito.never()).updateById(Mockito.any(ContractReview.class));
    }

    private ContractClause clause(String type, String name, String text) {
        ContractClause clause = new ContractClause();
        clause.setClauseType(type);
        clause.setClauseName(name);
        clause.setClauseText(text);
        return clause;
    }

    private CurrentUser user() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("tool:execute"), true);
    }

    private CurrentUser traceUser() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("trace:read"), true);
    }

    private ContractReview review(Long id, Long contractId, String status) {
        ContractReview review = new ContractReview();
        review.setId(id);
        review.setContractId(contractId);
        review.setStatus(status);
        return review;
    }

    private ClauseRisk highRisk() {
        ClauseRisk risk = new ClauseRisk();
        risk.setRiskLevel("HIGH");
        risk.setRequiresApproval(true);
        return risk;
    }

    private AgentStep step(String type, OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        AgentStep step = new AgentStep();
        step.setStepType(type);
        step.setStatus("COMPLETED");
        step.setStartedAt(startedAt);
        step.setFinishedAt(finishedAt);
        return step;
    }

    private LlmCallLog llm(Integer totalTokens, Long latencyMs) {
        LlmCallLog log = new LlmCallLog();
        log.setTotalTokens(totalTokens);
        log.setLatencyMs(latencyMs);
        return log;
    }

    private ToolCallLog tool(Long latencyMs) {
        ToolCallLog log = new ToolCallLog();
        log.setLatencyMs(latencyMs);
        return log;
    }

    private RetrievalLog retrieval(Long latencyMs) {
        RetrievalLog log = new RetrievalLog();
        log.setLatencyMs(latencyMs);
        return log;
    }

    private ContractReviewService service(LlmGateway gateway, LlmCallLogMapper logMapper) {
        return new ContractReviewService(
                Mockito.mock(ContractReviewMapper.class),
                Mockito.mock(AgentStepMapper.class),
                Mockito.mock(AgentStateTransitionLogMapper.class),
                Mockito.mock(ContractService.class),
                Mockito.mock(ClauseExtractionService.class),
                Mockito.mock(RagRetrievalService.class),
                Mockito.mock(RiskDiscoveryService.class),
                Mockito.mock(RiskAnalysisService.class),
                Mockito.mock(ReviewEventBus.class),
                Mockito.mock(ReviewJobPublisher.class),
                Mockito.mock(AppUserMapper.class),
                Mockito.mock(UserPermissionMapper.class),
                Mockito.mock(ApprovalService.class),
                logMapper,
                gateway,
                Mockito.mock(ToolCallLogMapper.class),
                Mockito.mock(RetrievalLogMapper.class),
                Mockito.mock(ReviewProgressRecorder.class)
        );
    }

    private ContractReviewService service(ContractReviewMapper reviewMapper, AgentStepMapper stepMapper,
                                          AgentStateTransitionLogMapper transitionMapper,
                                          LlmCallLogMapper llmMapper, ToolCallLogMapper toolMapper,
                                          RetrievalLogMapper retrievalMapper) {
        return service(reviewMapper, stepMapper, transitionMapper, llmMapper, toolMapper, retrievalMapper,
                Mockito.mock(ClauseExtractionService.class), Mockito.mock(RiskAnalysisService.class),
                Mockito.mock(ReviewProgressRecorder.class));
    }

    private ContractReviewService service(ContractReviewMapper reviewMapper, AgentStepMapper stepMapper,
                                          AgentStateTransitionLogMapper transitionMapper,
                                          LlmCallLogMapper llmMapper, ToolCallLogMapper toolMapper,
                                          RetrievalLogMapper retrievalMapper,
                                          ClauseExtractionService clauseExtractionService,
                                          RiskAnalysisService riskAnalysisService,
                                          ReviewProgressRecorder progressRecorder) {
        return new ContractReviewService(
                reviewMapper,
                stepMapper,
                transitionMapper,
                Mockito.mock(ContractService.class),
                clauseExtractionService,
                Mockito.mock(RagRetrievalService.class),
                Mockito.mock(RiskDiscoveryService.class),
                riskAnalysisService,
                Mockito.mock(ReviewEventBus.class),
                Mockito.mock(ReviewJobPublisher.class),
                Mockito.mock(AppUserMapper.class),
                Mockito.mock(UserPermissionMapper.class),
                Mockito.mock(ApprovalService.class),
                llmMapper,
                Mockito.mock(LlmGateway.class),
                toolMapper,
                retrievalMapper,
                progressRecorder
        );
    }
}
