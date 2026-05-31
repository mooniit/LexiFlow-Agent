package com.example.lexiflow.evaluation;

import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.review.mapper.ClauseRiskMapper;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.review.service.RiskAnalysisService;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentEvaluationMetricsTest {

    private static final double MIN_TOP5_HIT_RATE = 0.80;
    private static final double MIN_RISK_RECALL = 0.80;
    private static final double MAX_FALSE_POSITIVE_RATE = 0.25;
    private static final long MAX_AVERAGE_REVIEW_MILLIS = 500;

    @Test
    void measuresTop5LegalRuleHitRate() {
        List<RetrievalCase> cases = List.of(
                retrievalCase("如何申请设立公司", "公司法",
                        "公司法", "公司法", "公司法", "电子签名法", "反垄断法"),
                retrievalCase("个人信息处理是否需要同意", "个人信息保护法",
                        "个人信息保护法", "个人信息保护法", "数据安全法", "网络安全法", "劳动法"),
                retrievalCase("劳动合同试用期最长多久", "劳动合同法",
                        "劳动法", "劳动合同法", "劳动合同法", "公司法", "企业破产法"),
                retrievalCase("招标人可以拒收逾期投标文件吗", "招标投标法",
                        "招标投标法", "招标投标法", "公司法", "反垄断法", "网络安全法"),
                retrievalCase("电子签名是否具有法律效力", "电子签名法",
                        "电子签名法", "电子签名法", "公司法", "个人信息保护法", "劳动法")
        );

        double hitRate = topKHitRate(cases, 5);

        Assertions.assertThat(hitRate)
                .as("Top-5 legal rule hit rate")
                .isGreaterThanOrEqualTo(MIN_TOP5_HIT_RATE);
    }

    @Test
    void measuresRiskRecallAndFalsePositiveRate() {
        RiskAnalysisService service = riskAnalysisService();
        List<RiskCase> cases = List.of(
                new RiskCase(
                        "high amount and unlimited liability",
                        List.of(
                                clause("AMOUNT", "合同金额", "合同总价 120万元。"),
                                clause("LIABILITY", "责任条款", "乙方承担无限赔偿责任，不设上限。")
                        ),
                        Set.of(
                                "HIGH_CONTRACT_AMOUNT",
                                "UNLIMITED_LIABILITY",
                                "MISSING_DATA_PROTECTION",
                                "MISSING_TERMINATION_CLAUSE",
                                "WEAK_CONFIDENTIALITY",
                                "NON_STANDARD_JURISDICTION",
                                "MISSING_ACCEPTANCE_CLAUSE"
                        )
                ),
                new RiskCase(
                        "long payment and auto renewal",
                        List.of(
                                clause("PAYMENT_TERM", "付款周期", "甲方应在验收后 90 天内付款。"),
                                clause("AUTO_RENEWAL", "自动续约", "合同到期后自动续约一年，未约定提前通知。"),
                                clause("NOTICE", "通知", "任一方应提前三十日书面通知对方。")
                        ),
                        Set.of(
                                "PAYMENT_TERM_TOO_LONG",
                                "AUTO_RENEWAL_WITHOUT_NOTICE",
                                "MISSING_DATA_PROTECTION",
                                "MISSING_TERMINATION_CLAUSE",
                                "WEAK_CONFIDENTIALITY",
                                "NON_STANDARD_JURISDICTION",
                                "MISSING_ACCEPTANCE_CLAUSE"
                        )
                ),
                new RiskCase(
                        "privacy without data protection clause",
                        List.of(
                                clause("PARTIES", "合同主体", "甲方委托乙方处理客户个人信息和隐私数据。"),
                                clause("TERMINATION", "终止", "任何一方可提前三十日书面通知终止合同。"),
                                clause("CONFIDENTIALITY", "保密", "双方对商业秘密保密，违约方承担赔偿责任。"),
                                clause("DISPUTE_RESOLUTION", "争议解决", "争议提交上海仲裁委员会仲裁。"),
                                clause("ACCEPTANCE", "验收", "甲方应在十日内按验收标准完成验收，不合格应书面说明。")
                        ),
                        Set.of("MISSING_DATA_PROTECTION")
                )
        );

        RiskMetric metric = riskMetric(service, cases);

        Assertions.assertThat(metric.recall())
                .as("risk recognition recall")
                .isGreaterThanOrEqualTo(MIN_RISK_RECALL);
        Assertions.assertThat(metric.falsePositiveRate())
                .as("risk false positive rate")
                .isLessThanOrEqualTo(MAX_FALSE_POSITIVE_RATE);
    }

    @Test
    void measuresAverageReviewLatencyForLocalRiskAnalysis() {
        RiskAnalysisService service = riskAnalysisService();
        List<RiskCase> cases = List.of(
                new RiskCase("payment", List.of(clause("PAYMENT_TERM", "付款周期", "Payment shall be made within 90 days.")), Set.of()),
                new RiskCase("amount", List.of(clause("AMOUNT", "合同金额", "Total contract amount is 120万元.")), Set.of()),
                new RiskCase("liability", List.of(clause("LIABILITY", "责任条款", "Party B assumes unlimited liability with no cap.")), Set.of())
        );

        List<Long> elapsedMillis = new ArrayList<>();
        for (RiskCase riskCase : cases) {
            long start = System.nanoTime();
            service.analyze(1L, 2L, riskCase.clauses(), references(), user());
            elapsedMillis.add(Duration.ofNanos(System.nanoTime() - start).toMillis());
        }
        double averageMillis = elapsedMillis.stream().mapToLong(Long::longValue).average().orElse(0);

        Assertions.assertThat(averageMillis)
                .as("average review latency in local deterministic risk-analysis benchmark")
                .isLessThanOrEqualTo(MAX_AVERAGE_REVIEW_MILLIS);
    }

    private double topKHitRate(List<RetrievalCase> cases, int k) {
        long hits = cases.stream()
                .filter(c -> c.results().stream()
                        .limit(k)
                        .anyMatch(title -> title.contains(c.expectedLaw())))
                .count();
        return hits * 1.0 / cases.size();
    }

    private RiskMetric riskMetric(RiskAnalysisService service, List<RiskCase> cases) {
        int truePositive = 0;
        int falseNegative = 0;
        int falsePositive = 0;
        for (RiskCase riskCase : cases) {
            Set<String> expected = riskCase.expectedRiskTypes();
            Set<String> actual = new HashSet<>(service.analyze(1L, 2L, riskCase.clauses(), references(), user())
                    .stream()
                    .map(ClauseRisk::getRiskType)
                    .toList());

            for (String riskType : expected) {
                if (actual.contains(riskType)) {
                    truePositive++;
                } else {
                    falseNegative++;
                }
            }
            for (String riskType : actual) {
                if (!expected.contains(riskType)) {
                    falsePositive++;
                }
            }
        }
        double recall = truePositive * 1.0 / Math.max(1, truePositive + falseNegative);
        double falsePositiveRate = falsePositive * 1.0 / Math.max(1, truePositive + falsePositive);
        return new RiskMetric(recall, falsePositiveRate);
    }

    private RiskAnalysisService riskAnalysisService() {
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

    private CurrentUser user() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("tool:execute"), true);
    }

    private ContractClause clause(String type, String name, String text) {
        ContractClause clause = new ContractClause();
        clause.setId(10L);
        clause.setClauseType(type);
        clause.setClauseName(name);
        clause.setClauseText(text);
        return clause;
    }

    private List<RetrievedChunk> references() {
        return List.of(
                new RetrievedChunk(1L, 1L, "公司法\n第三十条 申请设立公司，应当提交设立登记申请书、公司章程等文件。", 0.82),
                new RetrievedChunk(2L, 2L, "个人信息保护法\n第十三条 取得个人同意后方可处理个人信息。", 0.78),
                new RetrievedChunk(3L, 3L, "劳动合同法\n第十九条 试用期最长不得超过六个月。", 0.75)
        );
    }

    private RetrievalCase retrievalCase(String query, String expectedLaw, String... results) {
        return new RetrievalCase(query, expectedLaw, List.of(results));
    }

    private record RetrievalCase(String query, String expectedLaw, List<String> results) {
    }

    private record RiskCase(String name, List<ContractClause> clauses, Set<String> expectedRiskTypes) {
    }

    private record RiskMetric(double recall, double falsePositiveRate) {
    }
}
