package com.example.lexiflow.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.review.mapper.ClauseRiskMapper;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAnalysisService {

    private static final Pattern DAYS_PATTERN = Pattern.compile("(?i)([0-9]{2,3})\\s*(?:日|天|days?)");

    private final ClauseRiskMapper riskMapper;
    private final ToolPermissionGuard toolPermissionGuard;

    public RiskAnalysisService(ClauseRiskMapper riskMapper, ToolPermissionGuard toolPermissionGuard) {
        this.riskMapper = riskMapper;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    @Transactional
    public List<ClauseRisk> analyze(Long reviewId, Long contractId, List<ContractClause> clauses,
                                    List<RetrievedChunk> references, CurrentUser user) {
        toolPermissionGuard.requireAllowed("risk_analysis", user);
        Long userId = user.id();
        riskMapper.delete(new LambdaQueryWrapper<ClauseRisk>().eq(ClauseRisk::getReviewId, reviewId));
        List<ClauseRisk> risks = new ArrayList<>();
        paymentRisk(reviewId, contractId, clauses, references, userId).ifPresent(risks::add);
        highAmountRisk(reviewId, contractId, clauses, references, userId).ifPresent(risks::add);
        unlimitedLiabilityRisk(reviewId, contractId, clauses, references, userId).ifPresent(risks::add);
        dataProtectionRisk(reviewId, contractId, clauses, references, userId).ifPresent(risks::add);
        autoRenewalRisk(reviewId, contractId, clauses, references, userId).ifPresent(risks::add);
        for (ClauseRisk risk : risks) {
            riskMapper.insert(risk);
        }
        return risks;
    }

    public List<ClauseRisk> listByReview(Long reviewId) {
        return riskMapper.selectList(new LambdaQueryWrapper<ClauseRisk>()
                .eq(ClauseRisk::getReviewId, reviewId)
                .eq(ClauseRisk::getDeleted, false)
                .orderByDesc(ClauseRisk::getRiskLevel));
    }

    private Optional<ClauseRisk> paymentRisk(Long reviewId, Long contractId, List<ContractClause> clauses,
                                             List<RetrievedChunk> references, Long userId) {
        Optional<ContractClause> payment = find(clauses, "PAYMENT_TERM");
        if (payment.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = DAYS_PATTERN.matcher(payment.get().getClauseText());
        if (matcher.find() && Integer.parseInt(matcher.group(1)) > 60) {
            return Optional.of(build(reviewId, contractId, payment.get(), "PAYMENT_TERM_TOO_LONG", "MEDIUM",
                    "付款周期超过 60 天，可能影响现金流并偏离常见合同审查要求。",
                    "建议将付款周期调整为 30-60 天，或补充逾期付款违约责任。",
                    references, false, userId));
        }
        return Optional.empty();
    }

    private Optional<ClauseRisk> highAmountRisk(Long reviewId, Long contractId, List<ContractClause> clauses,
                                                List<RetrievedChunk> references, Long userId) {
        Optional<ContractClause> amount = find(clauses, "AMOUNT");
        if (amount.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(amount.get().getClauseText());
        if (!matcher.find()) {
            return Optional.empty();
        }
        double value = Double.parseDouble(matcher.group(1));
        String content = amount.get().getClauseText().toLowerCase(Locale.ROOT);
        double normalizedAmount = (content.contains("万") || content.contains("涓囧厓")) ? value * 10000 : value;
        if (normalizedAmount >= 1_000_000) {
            return Optional.of(build(reviewId, contractId, amount.get(), "HIGH_CONTRACT_AMOUNT", "HIGH",
                    "合同金额达到高额阈值，超出自动审查直接放行范围。",
                    "建议由法务负责人或业务负责人确认付款、责任、验收和终止条款是否匹配该金额级别。",
                    references, true, userId));
        }
        return Optional.empty();
    }

    private Optional<ClauseRisk> unlimitedLiabilityRisk(Long reviewId, Long contractId, List<ContractClause> clauses,
                                                        List<RetrievedChunk> references, Long userId) {
        Optional<ContractClause> liability = find(clauses, "LIABILITY");
        if (liability.isEmpty()) {
            return Optional.empty();
        }
        String content = liability.get().getClauseText().toLowerCase(Locale.ROOT);
        if (content.contains("无限") || content.contains("不设上限") || content.contains("unlimited")) {
            return Optional.of(build(reviewId, contractId, liability.get(), "UNLIMITED_LIABILITY", "HIGH",
                    "责任条款存在无限责任或未设置赔偿上限表述。",
                    "建议设置责任上限，通常可与合同金额、年度费用或直接损失范围挂钩。",
                    references, true, userId));
        }
        return Optional.empty();
    }

    private Optional<ClauseRisk> dataProtectionRisk(Long reviewId, Long contractId, List<ContractClause> clauses,
                                                    List<RetrievedChunk> references, Long userId) {
        boolean hasPersonalDataSignal = clauses.stream()
                .map(ContractClause::getClauseText)
                .filter(content -> content != null)
                .map(content -> content.toLowerCase(Locale.ROOT))
                .anyMatch(content -> content.contains("个人信息") || content.contains("personal data") || content.contains("隐私"));
        if (hasPersonalDataSignal && find(clauses, "DATA_PROTECTION").isEmpty()) {
            return Optional.of(build(reviewId, contractId, null, "MISSING_DATA_PROTECTION", "HIGH",
                    "合同涉及个人信息或隐私事项，但未识别到独立数据保护条款。",
                    "建议补充数据处理目的、授权范围、安全措施、跨境传输和泄露通知等要求。",
                    references, true, userId));
        }
        if (find(clauses, "DATA_PROTECTION").isEmpty()) {
            return Optional.of(build(reviewId, contractId, null, "MISSING_DATA_PROTECTION", "MEDIUM",
                    "未识别到数据保护条款，若合同履行涉及数据处理将存在合规缺口。",
                    "建议确认是否涉及个人信息、商业数据或系统接入，并按需要补充数据保护条款。",
                    references, false, userId));
        }
        return Optional.empty();
    }

    private Optional<ClauseRisk> autoRenewalRisk(Long reviewId, Long contractId, List<ContractClause> clauses,
                                                 List<RetrievedChunk> references, Long userId) {
        Optional<ContractClause> renewal = find(clauses, "AUTO_RENEWAL");
        return renewal.map(clause -> build(reviewId, contractId, clause, "AUTO_RENEWAL_WITHOUT_NOTICE", "MEDIUM",
                "合同包含自动续约安排，可能造成未经复核的持续义务。",
                "建议增加续约前通知、退出窗口和价格/服务条款复核机制。",
                references, false, userId));
    }

    private Optional<ContractClause> find(List<ContractClause> clauses, String type) {
        return clauses.stream().filter(clause -> type.equals(clause.getClauseType())).findFirst();
    }

    private ClauseRisk build(Long reviewId, Long contractId, ContractClause clause, String riskType, String riskLevel,
                             String reason, String suggestion, List<RetrievedChunk> references,
                             boolean requiresApproval, Long userId) {
        ClauseRisk risk = new ClauseRisk();
        risk.setReviewId(reviewId);
        risk.setClauseId(clause == null ? null : clause.getId());
        risk.setRiskType(riskType);
        risk.setRiskLevel(riskLevel);
        risk.setClauseName(clause == null ? null : clause.getClauseName());
        risk.setClauseText(clause == null ? null : clause.getClauseText());
        risk.setReason(reason);
        risk.setSuggestion(suggestion);
        risk.setEvidenceRules(toEvidence(references));
        risk.setRequiresApproval(requiresApproval);
        risk.setReviewStatus("OPEN");
        risk.setCreatedBy(userId);
        risk.setUpdatedBy(userId);
        return risk;
    }

    private String toEvidence(List<RetrievedChunk> references) {
        return "{\"references\":[" + references.stream()
                .limit(3)
                .map(ref -> "{\"chunkId\":" + ref.chunkId()
                        + ",\"documentId\":" + ref.documentId()
                        + ",\"score\":" + String.format(Locale.ROOT, "%.4f", ref.score())
                        + ",\"content\":" + JsonStrings.quote(ref.content()) + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]}";
    }
}
