package com.example.lexiflow.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.review.mapper.ClauseRiskMapper;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAnalysisService {

    private static final Pattern DAYS_PATTERN = Pattern.compile("(?i)([0-9]{1,3})\\s*(?:日|天|days?)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(万元|万|元|rmb|cny|usd|美元)?", Pattern.CASE_INSENSITIVE);

    private final ClauseRiskMapper riskMapper;
    private final ToolPermissionGuard toolPermissionGuard;
    private final LlmGateway llmGateway;
    private final int paymentTermWarningDays;
    private final double highAmountThreshold;

    public RiskAnalysisService(ClauseRiskMapper riskMapper, ToolPermissionGuard toolPermissionGuard,
                               LlmGateway llmGateway,
                               @Value("${lexiflow.review.payment-term-warning-days:60}") int paymentTermWarningDays,
                               @Value("${lexiflow.review.high-amount-threshold:1000000}") double highAmountThreshold) {
        this.riskMapper = riskMapper;
        this.toolPermissionGuard = toolPermissionGuard;
        this.llmGateway = llmGateway;
        this.paymentTermWarningDays = paymentTermWarningDays;
        this.highAmountThreshold = highAmountThreshold;
    }

    @Transactional
    public List<ClauseRisk> analyze(Long reviewId, Long contractId, List<ContractClause> clauses,
                                    List<RetrievedChunk> references, CurrentUser user) {
        toolPermissionGuard.requireAllowed("risk_analysis", user);
        Long userId = user.id();
        riskMapper.delete(new LambdaQueryWrapper<ClauseRisk>().eq(ClauseRisk::getReviewId, reviewId));

        List<RiskCandidate> candidates = List.of(
                paymentTermStrategy(clauses, references),
                highAmountStrategy(clauses, references),
                unlimitedLiabilityStrategy(clauses, references),
                missingDataProtectionStrategy(clauses, references),
                missingTerminationStrategy(clauses, references),
                weakConfidentialityStrategy(clauses, references),
                ipOwnershipStrategy(clauses, references),
                disputeResolutionStrategy(clauses, references),
                acceptanceStrategy(clauses, references),
                autoRenewalStrategy(clauses, references)
        ).stream().flatMap(Optional::stream).toList();

        List<ClauseRisk> risks = new ArrayList<>();
        for (RiskCandidate candidate : candidates) {
            ClauseRisk risk = build(reviewId, contractId, candidate, userId);
            LlmEnhancement llmEnhancement = enhanceWithLlm(risk, references);
            risk.setEvidenceRules(toEvidence(candidate.references(), llmEnhancement));
            riskMapper.insert(risk);
            risks.add(risk);
        }
        return risks;
    }

    public List<ClauseRisk> listByReview(Long reviewId) {
        return riskMapper.selectList(new LambdaQueryWrapper<ClauseRisk>()
                .eq(ClauseRisk::getReviewId, reviewId)
                .eq(ClauseRisk::getDeleted, false)
                .orderByDesc(ClauseRisk::getRiskLevel));
    }

    private Optional<RiskCandidate> paymentTermStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        Optional<ContractClause> clause = find(clauses, "PAYMENT_TERM");
        if (clause.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = DAYS_PATTERN.matcher(safe(clause.get().getClauseText()));
        if (!matcher.find()) {
            return Optional.empty();
        }
        int days = Integer.parseInt(matcher.group(1));
        if (days <= paymentTermWarningDays) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                clause.get(),
                "PAYMENT_TERM_TOO_LONG",
                "MEDIUM",
                "付款周期为 " + days + " 天，超过当前配置的 " + paymentTermWarningDays + " 天阈值。",
                "建议缩短付款周期，或补充逾期利息、分阶段付款和审批说明。",
                evidence(references, content -> containsAny(content, "付款", "账期", "期限", "payment", "overdue")),
                false
        ));
    }

    private Optional<RiskCandidate> highAmountStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        Optional<ContractClause> clause = find(clauses, "AMOUNT");
        if (clause.isEmpty()) {
            return Optional.empty();
        }
        Optional<Double> amount = parseAmount(clause.get().getClauseText());
        if (amount.isEmpty() || amount.get() < highAmountThreshold) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                clause.get(),
                "HIGH_CONTRACT_AMOUNT",
                "HIGH",
                "合同金额为 " + String.format(Locale.ROOT, "%.2f", amount.get())
                        + "，达到当前配置的高金额阈值 "
                        + String.format(Locale.ROOT, "%.2f", highAmountThreshold) + "。",
                "建议要求法务或业务负责人审批，并重点复核付款、责任、验收和终止条款。",
                evidence(references, content -> containsAny(content, "金额", "价款", "付款", "责任", "amount", "liability")),
                true
        ));
    }

    private Optional<RiskCandidate> unlimitedLiabilityStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        Optional<ContractClause> clause = find(clauses, "LIABILITY");
        if (clause.isEmpty()) {
            return Optional.empty();
        }
        String content = safe(clause.get().getClauseText()).toLowerCase(Locale.ROOT);
        if (!containsAny(content, "无限", "不设上限", "unlimited", "no cap")) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                clause.get(),
                "UNLIMITED_LIABILITY",
                "HIGH",
                "责任条款包含无限责任或未设置责任上限的表述。",
                "建议设置与合同金额、年度费用或直接损失范围挂钩的责任上限，并明确例外情形。",
                evidence(references, text -> containsAny(text, "责任", "赔偿", "上限", "liability", "cap")),
                true
        ));
    }

    private Optional<RiskCandidate> missingDataProtectionStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        boolean hasDataClause = find(clauses, "DATA_PROTECTION").isPresent();
        if (hasDataClause) {
            return Optional.empty();
        }
        boolean hasPersonalDataSignal = clauses.stream()
                .map(ContractClause::getClauseText)
                .map(this::safe)
                .map(text -> text.toLowerCase(Locale.ROOT))
                .anyMatch(text -> containsAny(text, "个人信息", "隐私", "personal data", "privacy"));
        return Optional.of(new RiskCandidate(
                null,
                "MISSING_DATA_PROTECTION",
                hasPersonalDataSignal ? "HIGH" : "MEDIUM",
                hasPersonalDataSignal
                        ? "合同提及个人信息或隐私，但未识别到独立的数据保护条款。"
                        : "未识别到数据保护条款；如果履约涉及数据处理，可能存在合规缺口。",
                "建议确认数据处理范围，并按需补充处理目的、授权范围、安全措施、跨境传输和泄露通知要求。",
                evidence(references, text -> containsAny(text, "个人信息", "数据", "隐私", "personal data", "privacy")),
                hasPersonalDataSignal
        ));
    }

    private Optional<RiskCandidate> missingTerminationStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        if (find(clauses, "TERMINATION").isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                null,
                "MISSING_TERMINATION_CLAUSE",
                "MEDIUM",
                "未识别到解除或终止条款，退出条件、通知期限和终止后义务可能不清晰。",
                "建议补充终止事由、通知期限、补救期、费用结算、数据返还和持续有效义务。",
                evidence(references, text -> containsAny(text, "解除", "终止", "通知", "termination", "notice")),
                false
        ));
    }

    private Optional<RiskCandidate> weakConfidentialityStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        Optional<ContractClause> clause = find(clauses, "CONFIDENTIALITY");
        if (clause.isEmpty()) {
            return Optional.of(new RiskCandidate(
                    null,
                    "WEAK_CONFIDENTIALITY",
                    "MEDIUM",
                    "未识别到保密条款，商业秘密和业务信息可能缺少保护。",
                    "建议补充保密范围、允许披露情形、保护标准、保密期限、违约责任和资料返还/销毁义务。",
                    evidence(references, text -> containsAny(text, "保密", "商业秘密", "confidential")),
                    false
            ));
        }
        String content = safe(clause.get().getClauseText()).toLowerCase(Locale.ROOT);
        if (containsAny(content, "期限", "违约", "赔偿", "责任", "duration", "liability")) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                clause.get(),
                "WEAK_CONFIDENTIALITY",
                "MEDIUM",
                "已识别到保密表述，但保密期限或违约责任不够完整。",
                "建议明确保密期限、例外情形、违约责任以及资料返还或销毁要求。",
                evidence(references, text -> containsAny(text, "保密", "商业秘密", "责任", "confidential")),
                false
        ));
    }

    private Optional<RiskCandidate> ipOwnershipStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        Optional<ContractClause> clause = find(clauses, "INTELLECTUAL_PROPERTY");
        if (clause.isEmpty()) {
            return Optional.empty();
        }
        String content = safe(clause.get().getClauseText()).toLowerCase(Locale.ROOT);
        if (containsAny(content, "归属", "所有权", "许可", "授权", "ownership", "license")) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                clause.get(),
                "IP_OWNERSHIP_UNCLEAR",
                "MEDIUM",
                "合同提及知识产权，但未清楚约定归属、许可范围或交付成果权利。",
                "建议明确背景知识产权、新产生知识产权、许可范围、使用限制和侵权责任。",
                evidence(references, text -> containsAny(text, "知识产权", "著作权", "专利", "许可", "intellectual property", "license")),
                false
        ));
    }

    private Optional<RiskCandidate> disputeResolutionStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        Optional<ContractClause> clause = find(clauses, "DISPUTE_RESOLUTION");
        if (clause.isEmpty()) {
            return Optional.of(new RiskCandidate(
                    null,
                    "NON_STANDARD_JURISDICTION",
                    "MEDIUM",
                    "未识别到争议解决条款，管辖机构和处理程序可能不确定。",
                    "建议补充适用法律、协商流程、仲裁或法院管辖以及地点。",
                    evidence(references, text -> containsAny(text, "争议", "仲裁", "诉讼", "法院", "dispute", "arbitration")),
                    false
            ));
        }
        String content = safe(clause.get().getClauseText()).toLowerCase(Locale.ROOT);
        if (containsAny(content, "仲裁", "法院", "管辖", "arbitration", "court", "jurisdiction")) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                clause.get(),
                "NON_STANDARD_JURISDICTION",
                "MEDIUM",
                "已识别到争议解决表述，但仲裁机构、法院或管辖约定不清晰。",
                "建议明确争议处理机构、适用法律、管辖地和升级处理流程。",
                evidence(references, text -> containsAny(text, "争议", "仲裁", "诉讼", "法院", "dispute", "arbitration")),
                false
        ));
    }

    private Optional<RiskCandidate> acceptanceStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        Optional<ContractClause> clause = find(clauses, "ACCEPTANCE");
        if (clause.isEmpty()) {
            return Optional.of(new RiskCandidate(
                    null,
                    "MISSING_ACCEPTANCE_CLAUSE",
                    "MEDIUM",
                    "未识别到验收条款，交付标准、验收期限和视为验收规则可能不清晰。",
                    "建议补充交付成果、客观验收标准、测试期限、拒收流程和整改时限。",
                    evidence(references, text -> containsAny(text, "验收", "交付", "质量", "acceptance", "deliverable")),
                    false
            ));
        }
        String content = safe(clause.get().getClauseText()).toLowerCase(Locale.ROOT);
        if (containsAny(content, "标准", "期限", "不合格", "整改", "criteria", "period", "reject")) {
            return Optional.empty();
        }
        return Optional.of(new RiskCandidate(
                clause.get(),
                "ACCEPTANCE_STANDARD_WEAK",
                "MEDIUM",
                "已识别到验收条款，但验收标准、期限或拒收流程不够具体。",
                "建议明确可衡量的验收标准、审核期限、书面确认、拒收理由和整改流程。",
                evidence(references, text -> containsAny(text, "验收", "交付", "质量", "acceptance", "deliverable")),
                false
        ));
    }

    private Optional<RiskCandidate> autoRenewalStrategy(List<ContractClause> clauses, List<RetrievedChunk> references) {
        return find(clauses, "AUTO_RENEWAL").map(clause -> {
            String content = safe(clause.getClauseText()).toLowerCase(Locale.ROOT);
            String riskLevel = find(clauses, "NOTICE").isPresent() || containsAny(content, "通知", "提前", "notice", "opt-out")
                    ? "LOW"
                    : "MEDIUM";
            return new RiskCandidate(
                    clause,
                    "AUTO_RENEWAL_WITHOUT_NOTICE",
                    riskLevel,
                    "合同包含自动续约安排，可能形成未经复核的持续义务。",
                    "建议增加续约前通知、退出窗口以及续约前价格/服务条款复核机制。",
                    evidence(references, text -> containsAny(text, "续约", "通知", "期限", "renewal", "notice")),
                    false
            );
        });
    }

    private Optional<ContractClause> find(List<ContractClause> clauses, String type) {
        return clauses.stream().filter(clause -> type.equals(clause.getClauseType())).findFirst();
    }

    private Optional<Double> parseAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(safe(text));
        if (!matcher.find()) {
            return Optional.empty();
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
        if (unit.contains("万")) {
            value *= 10000;
        }
        return Optional.of(value);
    }

    private List<RetrievedChunk> evidence(List<RetrievedChunk> references, Predicate<String> matcher) {
        List<RetrievedChunk> matched = references.stream()
                .filter(ref -> matcher.test(safe(ref.content()).toLowerCase(Locale.ROOT)))
                .toList();
        return matched.isEmpty() ? references : matched;
    }

    private ClauseRisk build(Long reviewId, Long contractId, RiskCandidate candidate, Long userId) {
        ContractClause clause = candidate.clause();
        ClauseRisk risk = new ClauseRisk();
        risk.setReviewId(reviewId);
        risk.setClauseId(clause == null ? null : clause.getId());
        risk.setRiskType(candidate.riskType());
        risk.setRiskLevel(candidate.riskLevel());
        risk.setClauseName(clause == null ? null : clause.getClauseName());
        risk.setClauseText(clause == null ? null : clause.getClauseText());
        risk.setReason(candidate.reason());
        risk.setSuggestion(candidate.suggestion());
        risk.setEvidenceRules(toEvidence(candidate.references()));
        risk.setRequiresApproval(candidate.requiresApproval());
        risk.setReviewStatus("OPEN");
        risk.setCreatedBy(userId);
        risk.setUpdatedBy(userId);
        return risk;
    }

    private LlmEnhancement enhanceWithLlm(ClauseRisk risk, List<RetrievedChunk> references) {
        try {
            StructuredOutputResponse response = llmGateway.structuredOutput(new StructuredOutputRequest(
                    new ChatRequest("risk-analysis", null, List.of(
                            new ChatMessage(ChatMessage.Role.SYSTEM,
                                    "Improve the contract risk explanation. Do not invent law or facts. Return JSON fields reason, suggestion, riskLevel, requiresApproval."),
                            new ChatMessage(ChatMessage.Role.USER, riskPrompt(risk, references))
                    ), Map.of("temperature", 0)),
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "reason", Map.of("type", "string"),
                                    "suggestion", Map.of("type", "string"),
                                    "riskLevel", Map.of("type", "string"),
                                    "requiresApproval", Map.of("type", "boolean")
                            )
                    )
            ));
            Map<String, Object> data = response.data();
            applyLlmEnhancement(risk, data);
            return new LlmEnhancement(true, response.provider(), response.model());
        } catch (RuntimeException ignored) {
            // The deterministic strategy result remains valid when LLM is unavailable, mock-only, or malformed.
            return new LlmEnhancement(false, null, null);
        }
    }

    private String riskPrompt(ClauseRisk risk, List<RetrievedChunk> references) {
        return "Risk type: " + safe(risk.getRiskType())
                + "\nRisk level: " + safe(risk.getRiskLevel())
                + "\nRequires approval: " + risk.getRequiresApproval()
                + "\nClause name: " + safe(risk.getClauseName())
                + "\nClause text: " + safe(risk.getClauseText())
                + "\nInitial reason: " + safe(risk.getReason())
                + "\nInitial suggestion: " + safe(risk.getSuggestion())
                + "\nReferences:\n" + references.stream()
                .limit(3)
                .map(RetrievedChunk::content)
                .reduce((left, right) -> left + "\n---\n" + right)
                .orElse("");
    }

    private void applyLlmEnhancement(ClauseRisk risk, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        if (data.get("reason") instanceof String reason && !reason.isBlank()) {
            risk.setReason(reason);
        }
        if (data.get("suggestion") instanceof String suggestion && !suggestion.isBlank()) {
            risk.setSuggestion(suggestion);
        }
        if (data.get("riskLevel") instanceof String riskLevel && List.of("LOW", "MEDIUM", "HIGH").contains(riskLevel)) {
            risk.setRiskLevel(riskLevel);
        }
        if (data.get("requiresApproval") instanceof Boolean requiresApproval) {
            risk.setRequiresApproval(requiresApproval || "HIGH".equals(risk.getRiskLevel()));
        }
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String toEvidence(List<RetrievedChunk> references) {
        return toEvidence(references, new LlmEnhancement(false, null, null));
    }

    private String toEvidence(List<RetrievedChunk> references, LlmEnhancement llmEnhancement) {
        return "{\"llmEnhanced\":" + llmEnhancement.enhanced()
                + ",\"llmProvider\":" + JsonStrings.quote(llmEnhancement.provider())
                + ",\"llmModel\":" + JsonStrings.quote(llmEnhancement.model())
                + ",\"references\":[" + references.stream()
                .limit(3)
                .map(ref -> "{\"chunkId\":" + ref.chunkId()
                        + ",\"documentId\":" + ref.documentId()
                        + ",\"score\":" + String.format(Locale.ROOT, "%.4f", ref.score())
                        + ",\"content\":" + JsonStrings.quote(ref.content()) + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]}";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record RiskCandidate(ContractClause clause, String riskType, String riskLevel, String reason,
                                 String suggestion, List<RetrievedChunk> references, boolean requiresApproval) {
    }

    private record LlmEnhancement(boolean enhanced, String provider, String model) {
    }
}
