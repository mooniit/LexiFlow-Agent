package com.example.lexiflow.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.approval.service.ApprovalService;
import com.example.lexiflow.approval.service.ApprovalEventMessage;
import com.example.lexiflow.approval.model.ApprovalRequest;
import com.example.lexiflow.agent.model.AgentStepType;
import com.example.lexiflow.agent.model.AgentTaskStatus;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractStatus;
import com.example.lexiflow.contract.service.ClauseExtractionService;
import com.example.lexiflow.contract.service.ContractService;
import com.example.lexiflow.llm.mapper.LlmCallLogMapper;
import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.LlmCallLog;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.rag.mapper.RetrievalLogMapper;
import com.example.lexiflow.rag.model.RetrievalLog;
import com.example.lexiflow.rag.service.RagRetrievalService;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.review.mapper.AgentStateTransitionLogMapper;
import com.example.lexiflow.review.mapper.AgentStepMapper;
import com.example.lexiflow.review.mapper.ContractReviewMapper;
import com.example.lexiflow.review.model.AgentStateTransitionLog;
import com.example.lexiflow.review.model.AgentStep;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.review.model.ContractReview;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ToolCallLogMapper;
import com.example.lexiflow.tool.model.ToolCallLog;
import com.example.lexiflow.user.mapper.AppUserMapper;
import com.example.lexiflow.user.mapper.UserPermissionMapper;
import com.example.lexiflow.user.model.AppUser;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ContractReviewService {

    private static final Pattern DAYS_PATTERN = Pattern.compile("(?i)([0-9]{1,3})\\s*(?:日|天|days?)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(万元|万|元|rmb|cny|usd|美元)?", Pattern.CASE_INSENSITIVE);

    private final ContractReviewMapper reviewMapper;
    private final AgentStepMapper stepMapper;
    private final AgentStateTransitionLogMapper transitionLogMapper;
    private final ContractService contractService;
    private final ClauseExtractionService clauseExtractionService;
    private final RagRetrievalService retrievalService;
    private final RiskDiscoveryService riskDiscoveryService;
    private final RiskAnalysisService riskAnalysisService;
    private final ReviewEventBus eventBus;
    private final ReviewJobPublisher reviewJobPublisher;
    private final AppUserMapper userMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final ApprovalService approvalService;
    private final LlmCallLogMapper llmCallLogMapper;
    private final LlmGateway llmGateway;
    private final ToolCallLogMapper toolCallLogMapper;
    private final RetrievalLogMapper retrievalLogMapper;
    private final ReviewProgressRecorder progressRecorder;

    public ContractReviewService(ContractReviewMapper reviewMapper, AgentStepMapper stepMapper,
                                 AgentStateTransitionLogMapper transitionLogMapper, ContractService contractService,
                                 ClauseExtractionService clauseExtractionService, RagRetrievalService retrievalService,
                                 RiskDiscoveryService riskDiscoveryService,
                                 RiskAnalysisService riskAnalysisService,
                                 ReviewEventBus eventBus, ReviewJobPublisher reviewJobPublisher,
                                 AppUserMapper userMapper, UserPermissionMapper userPermissionMapper,
                                  ApprovalService approvalService, LlmCallLogMapper llmCallLogMapper,
                                  LlmGateway llmGateway,
                                  ToolCallLogMapper toolCallLogMapper, RetrievalLogMapper retrievalLogMapper,
                                  ReviewProgressRecorder progressRecorder) {
        this.reviewMapper = reviewMapper;
        this.stepMapper = stepMapper;
        this.transitionLogMapper = transitionLogMapper;
        this.contractService = contractService;
        this.clauseExtractionService = clauseExtractionService;
        this.retrievalService = retrievalService;
        this.riskDiscoveryService = riskDiscoveryService;
        this.riskAnalysisService = riskAnalysisService;
        this.eventBus = eventBus;
        this.reviewJobPublisher = reviewJobPublisher;
        this.userMapper = userMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.approvalService = approvalService;
        this.llmCallLogMapper = llmCallLogMapper;
        this.llmGateway = llmGateway;
        this.toolCallLogMapper = toolCallLogMapper;
        this.retrievalLogMapper = retrievalLogMapper;
        this.progressRecorder = progressRecorder;
    }

    @Transactional
    public ContractReview create(Long contractId, CurrentUser user) {
        contractService.requireById(contractId);
        ContractReview review = new ContractReview();
        review.setContractId(contractId);
        review.setStatus(AgentTaskStatus.CREATED.name());
        review.setProgressPercent(0);
        review.setResultSummary("{}");
        review.setCreatedBy(user.id());
        review.setUpdatedBy(user.id());
        reviewMapper.insert(review);
        logTransition(review.getId(), null, AgentTaskStatus.CREATED.name(), "Review task created.", user.id());
        eventBus.publish(review.getId(), "CREATED", "Review task created.");
        publishAfterCommit(review.getId(), user);
        return review;
    }

    public List<ContractReview> list(Long contractId) {
        LambdaQueryWrapper<ContractReview> query = new LambdaQueryWrapper<ContractReview>()
                .eq(ContractReview::getDeleted, false)
                .orderByDesc(ContractReview::getCreatedAt);
        if (contractId != null) {
            query.eq(ContractReview::getContractId, contractId);
        }
        return reviewMapper.selectList(query);
    }

    public ContractReview requireById(Long id) {
        ContractReview review = reviewMapper.selectById(id);
        if (review == null || Boolean.TRUE.equals(review.getDeleted())) {
            throw new IllegalArgumentException("Review not found: " + id);
        }
        return review;
    }

    public List<AgentStep> steps(Long reviewId) {
        requireById(reviewId);
        return stepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getReviewId, reviewId)
                .eq(AgentStep::getDeleted, false)
                .orderByAsc(AgentStep::getCreatedAt));
    }

    @Transactional
    public ContractReview cancel(Long reviewId, CurrentUser user) {
        requireById(reviewId);
        return progressRecorder.transition(reviewId, AgentTaskStatus.CANCELLED, "Review cancelled by user.", user.id(), 100);
    }

    @Transactional
    public ContractReview rerun(Long reviewId, CurrentUser user) {
        ContractReview review = requireById(reviewId);
        if (!AgentTaskStatus.FAILED.name().equals(review.getStatus())) {
            throw new IllegalArgumentException("Only failed review tasks can be rerun.");
        }
        progressRecorder.transition(reviewId, AgentTaskStatus.PARSING, "Review rerun requested.", user.id(), 10);
        publishAfterCommit(reviewId, user);
        return requireById(reviewId);
    }

    public void runQueuedReview(Long reviewId, Long userId) {
        CurrentUser user = loadCurrentUser(userId);
        ContractReview review = requireById(reviewId);
        if (AgentTaskStatus.CREATED.name().equals(review.getStatus())) {
            runInline(reviewId, user);
        } else if (AgentTaskStatus.PARSING.name().equals(review.getStatus())) {
            runFromParsing(review, user);
        }
    }

    private void runInline(Long reviewId, CurrentUser user) {
        ContractReview review = progressRecorder.transition(reviewId, AgentTaskStatus.PARSING, "Start contract parsing.", user.id(), 10);
        runFromParsing(review, user);
    }

    private void publishAfterCommit(Long reviewId, CurrentUser user) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reviewJobPublisher.publishReviewJob(reviewId, user);
                }
            });
        } else {
            reviewJobPublisher.publishReviewJob(reviewId, user);
        }
    }

    private CurrentUser loadCurrentUser(Long userId) {
        AppUser appUser = userMapper.selectById(userId);
        if (appUser == null || Boolean.TRUE.equals(appUser.getDeleted()) || Boolean.FALSE.equals(appUser.getEnabled())) {
            throw new IllegalArgumentException("Review user not available: " + userId);
        }
        return new CurrentUser(
                appUser.getId(),
                appUser.getUsername(),
                appUser.getDisplayName(),
                appUser.getDepartmentId(),
                userPermissionMapper.findRoleCodes(appUser.getId()),
                userPermissionMapper.findPermissionCodes(appUser.getId()),
                appUser.getEnabled()
        );
    }

    private void runFromParsing(ContractReview review, CurrentUser user) {
        AgentStep runningStep = null;
        try {
            runningStep = progressRecorder.beginStep(review.getId(), AgentStepType.CONTRACT_PARSE,
                    "{\"contractId\":" + review.getContractId() + "}", user.id());
            Instant parseStart = Instant.now();
            Contract contract = contractService.parse(review.getContractId(), user);
            logToolCall(review.getId(), "contract_parse",
                    "{\"contractId\":" + review.getContractId() + "}",
                    "{\"status\":" + JsonStrings.quote(contract.getStatus())
                            + ",\"textLength\":" + (contract.getParsedText() == null ? 0 : contract.getParsedText().length()) + "}",
                    true, null, Duration.between(parseStart, Instant.now()).toMillis(), user.id());
            if (!ContractStatus.PARSED.name().equals(contract.getStatus())) {
                progressRecorder.failStep(runningStep.getId(), "Contract parsing failed.", user.id());
                fail(review, "Contract parsing failed. Check contract metadata for parse_failure.", user.id());
                return;
            }
            progressRecorder.completeStep(runningStep.getId(), "{\"textLength\":" + contract.getParsedText().length() + "}", user.id());
            runningStep = null;

            review = progressRecorder.transition(review.getId(), AgentTaskStatus.EXTRACTING, "Extract contract clauses.", user.id(), 30);
            runningStep = progressRecorder.beginStep(review.getId(), AgentStepType.CLAUSE_EXTRACTION,
                    "{\"contractId\":" + review.getContractId() + "}", user.id());
            Instant extractionStart = Instant.now();
            List<ContractClause> clauses = clauseExtractionService.extract(contract, user);
            logToolCall(review.getId(), "clause_extraction",
                    "{\"contractId\":" + review.getContractId()
                            + ",\"textLength\":" + (contract.getParsedText() == null ? 0 : contract.getParsedText().length()) + "}",
                    "{\"clauseCount\":" + clauses.size()
                            + ",\"clauseTypes\":[" + clauseTypesJson(clauses) + "]}",
                    true, null, Duration.between(extractionStart, Instant.now()).toMillis(), user.id());
            progressRecorder.completeStep(runningStep.getId(), "{\"clauseCount\":" + clauses.size() + "}", user.id());
            runningStep = null;

            review = progressRecorder.transition(review.getId(), AgentTaskStatus.RETRIEVING_RULES, "Retrieve compliance rules.", user.id(), 50);
            runningStep = progressRecorder.beginStep(review.getId(), AgentStepType.RULE_RETRIEVAL,
                    "{\"contractId\":" + review.getContractId() + "}", user.id());
            List<ClauseInsight> clauseInsights = buildClauseInsights(review.getId(), clauses, user);
            List<ThemeRetrievalQuery> themeQueries = buildThemeRetrievalQueries(contract, clauseInsights);
            List<RetrievedChunk> references = retrieveThemeReferences(review.getId(), themeQueries, user);
            progressRecorder.completeStep(runningStep.getId(),
                    "{\"matchedRules\":" + references.size()
                            + ",\"themes\":[" + themesJson(themeQueries) + "]}",
                    user.id());
            runningStep = null;

            review = progressRecorder.transition(review.getId(), AgentTaskStatus.ANALYZING, "Analyze contract risks.", user.id(), 70);
            runningStep = progressRecorder.beginStep(review.getId(), AgentStepType.RISK_ANALYSIS,
                    "{\"contractId\":" + review.getContractId() + ",\"clauseCount\":" + clauses.size() + "}", user.id());
            Instant analysisStart = Instant.now();
            var discoveredRisks = riskDiscoveryService.discover(review.getId(), contract, clauseInsights, references, user);
            var risks = riskAnalysisService.analyze(review.getId(), review.getContractId(), clauses, references,
                    clauseInsights, discoveredRisks, user);
            logToolCall(review.getId(), "risk_analysis",
                    "{\"reviewId\":" + review.getId()
                            + ",\"contractId\":" + review.getContractId()
                            + ",\"clauseCount\":" + clauses.size()
                            + ",\"clauseInsightCount\":" + clauseInsights.size()
                            + ",\"discoveredRiskCount\":" + discoveredRisks.size()
                            + ",\"themeCount\":" + themeQueries.size()
                            + ",\"referenceCount\":" + references.size() + "}",
                    "{\"riskCount\":" + risks.size()
                            + ",\"overallRiskLevel\":" + JsonStrings.quote(overallRisk(risks)) + "}",
                    true, null, Duration.between(analysisStart, Instant.now()).toMillis(), user.id());
            progressRecorder.completeStep(runningStep.getId(), "{\"riskCount\":" + risks.size() + "}", user.id());
            runningStep = null;

            if (requiresApproval(risks)) {
                runningStep = progressRecorder.beginStep(review.getId(), AgentStepType.APPROVAL_REQUEST,
                        "{\"riskCount\":" + risks.size() + "}", user.id());
                approvalService.ensurePendingReviewApproval(review.getId(), risks, user);
                progressRecorder.completeStep(runningStep.getId(), "{\"status\":\"PENDING\"}", user.id());
                runningStep = null;
                review.setOverallRiskLevel(overallRisk(risks));
                review.setResultSummary("{\"summary\":\"High-risk review is waiting for manual approval.\",\"clauseCount\":"
                        + clauses.size() + ",\"riskCount\":" + risks.size() + ",\"overallRiskLevel\":\"" + overallRisk(risks)
                        + "\",\"approvalRequired\":true}");
                review.setUpdatedBy(user.id());
                review.setUpdatedAt(OffsetDateTime.now());
                reviewMapper.updateById(review);
                progressRecorder.transition(review.getId(), AgentTaskStatus.WAITING_APPROVAL, "High-risk review requires manual approval.", user.id(), 80);
                return;
            }
            generateReport(review, clauses.size(), risks, user, "Automated review completed.", false);
        } catch (RuntimeException ex) {
            if (runningStep != null && runningStep.getId() != null) {
                progressRecorder.failStep(runningStep.getId(), ex.getMessage(), user.id());
            }
            fail(review, ex.getMessage(), user.id());
        }
    }

    @Transactional
    public ContractReview resumeAfterApproval(Long reviewId, CurrentUser user) {
        ContractReview review = requireById(reviewId);
        if (!AgentTaskStatus.WAITING_APPROVAL.name().equals(review.getStatus())) {
            throw new IllegalArgumentException("Only waiting approval review tasks can be resumed.");
        }
        List<ClauseRisk> risks = riskAnalysisService.listByReview(reviewId);
        int clauseCount = clauseExtractionService.listByContract(review.getContractId()).size();
        generateReport(review, clauseCount, risks, user, "Manual approval passed and report generated.", true);
        return requireById(reviewId);
    }

    @Transactional
    public ContractReview rejectAfterApproval(Long reviewId, CurrentUser user, String reason) {
        ContractReview review = requireById(reviewId);
        if (!AgentTaskStatus.WAITING_APPROVAL.name().equals(review.getStatus())) {
            throw new IllegalArgumentException("Only waiting approval review tasks can be rejected.");
        }
        fail(review, reason, user.id());
        return requireById(reviewId);
    }

    @Transactional
    public ContractReview handleApprovalEvent(ApprovalEventMessage message) {
        CurrentUser user = loadCurrentUser(message.operatorId());
        ContractReview review = requireById(message.reviewId());
        if ("APPROVED".equals(message.action())) {
            if (AgentTaskStatus.COMPLETED.name().equals(review.getStatus())) {
                eventBus.publish(review.getId(), "APPROVAL_EVENT_IGNORED", "Approval event already applied.");
                return review;
            }
            return resumeAfterApproval(message.reviewId(), user);
        }
        if ("REJECTED".equals(message.action()) || "REVISION_REQUESTED".equals(message.action())) {
            if (AgentTaskStatus.FAILED.name().equals(review.getStatus())) {
                eventBus.publish(review.getId(), "APPROVAL_EVENT_IGNORED", "Approval event already applied.");
                return review;
            }
            return rejectAfterApproval(message.reviewId(), user, message.comment());
        }
        throw new IllegalArgumentException("Unsupported approval event action: " + message.action());
    }

    public ReviewReport report(Long reviewId) {
        ContractReview review = requireById(reviewId);
        Contract contract = contractService.requireById(review.getContractId());
        List<ClauseRisk> risks = riskAnalysisService.listByReview(reviewId);
        List<ApprovalRequest> approvals = approvalService.list(null, reviewId);
        return new ReviewReport(review, contract, risks, approvals, review.getResultSummary(), finalConclusion(review, risks));
    }

    public ReviewTrace trace(Long reviewId, CurrentUser user) {
        if (user == null || !user.permissions().contains("trace:read")) {
            throw new IllegalArgumentException("trace:read permission is required.");
        }
        ContractReview review = requireById(reviewId);
        List<AgentStep> steps = steps(reviewId);
        List<AgentStateTransitionLog> transitions = transitionLogMapper.selectList(
                new LambdaQueryWrapper<AgentStateTransitionLog>()
                        .eq(AgentStateTransitionLog::getReviewId, reviewId)
                        .orderByAsc(AgentStateTransitionLog::getCreatedAt));
        List<LlmCallLog> llmCalls = llmCallLogMapper.selectList(new LambdaQueryWrapper<LlmCallLog>()
                .eq(LlmCallLog::getReviewId, reviewId)
                .orderByAsc(LlmCallLog::getCreatedAt));
        List<ToolCallLog> toolCalls = toolCallLogMapper.selectList(new LambdaQueryWrapper<ToolCallLog>()
                .eq(ToolCallLog::getReviewId, reviewId)
                .orderByAsc(ToolCallLog::getCreatedAt));
        List<RetrievalLog> retrievalLogs = retrievalLogMapper.selectList(new LambdaQueryWrapper<RetrievalLog>()
                .eq(RetrievalLog::getReviewId, reviewId)
                .orderByAsc(RetrievalLog::getCreatedAt));
        OffsetDateTime now = OffsetDateTime.now();
        long stepDurationMs = steps.stream()
                .mapToLong(step -> stepDurationMs(step, now))
                .sum();
        long wallClockMs = wallClockMs(steps, now);
        long llmLatencyMs = llmCalls.stream()
                .map(LlmCallLog::getLatencyMs)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long toolLatencyMs = toolCalls.stream()
                .map(ToolCallLog::getLatencyMs)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long retrievalLatencyMs = retrievalLogs.stream()
                .map(RetrievalLog::getLatencyMs)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        int totalTokens = llmCalls.stream()
                .map(LlmCallLog::getTotalTokens)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return new ReviewTrace(review, steps, transitions, llmCalls, toolCalls, retrievalLogs,
                new TraceMetrics(steps.size(), llmCalls.size(), toolCalls.size(), retrievalLogs.size(), totalTokens,
                        wallClockMs, stepDurationMs, wallClockMs, toolLatencyMs, llmLatencyMs, retrievalLatencyMs));
    }

    private void generateReport(ContractReview review, int clauseCount, List<ClauseRisk> risks, CurrentUser user,
                                String summary, boolean approvalPassed) {
        Instant reportStart = Instant.now();
        review = progressRecorder.transition(review.getId(), AgentTaskStatus.GENERATING_REPORT, "Generate review report.", user.id(), 90);
        AgentStep reportStep = progressRecorder.beginStep(review.getId(), AgentStepType.REPORT_GENERATION, "{}", user.id());
        String overallRisk = overallRisk(risks);
        String resultSummary = "{\"summary\":\"" + summary + "\",\"clauseCount\":"
                + clauseCount + ",\"riskCount\":" + risks.size() + ",\"overallRiskLevel\":\"" + overallRisk
                + "\",\"approvalRequired\":" + requiresApproval(risks) + ",\"approvalPassed\":" + approvalPassed + "}";
        review = progressRecorder.updateReviewResult(review.getId(), overallRisk, resultSummary, user.id());
        progressRecorder.completeStep(reportStep.getId(), "{\"summary\":\"" + summary + "\"}", user.id());
        logToolCall(review.getId(), "report_generation",
                "{\"reviewId\":" + review.getId() + ",\"clauseCount\":" + clauseCount
                        + ",\"riskCount\":" + risks.size() + "}",
                "{\"summary\":" + JsonStrings.quote(summary)
                        + ",\"overallRiskLevel\":" + JsonStrings.quote(overallRisk)
                        + ",\"approvalPassed\":" + approvalPassed + "}",
                true, null, Duration.between(reportStart, Instant.now()).toMillis(), user.id());
        progressRecorder.transition(review.getId(), AgentTaskStatus.COMPLETED, "Review completed.", user.id(), 100);
    }

    private boolean requiresApproval(List<ClauseRisk> risks) {
        return risks.stream().anyMatch(risk -> "HIGH".equals(risk.getRiskLevel())
                || Boolean.TRUE.equals(risk.getRequiresApproval()));
    }

    private String overallRisk(List<ClauseRisk> risks) {
        return risks.stream().anyMatch(risk -> "HIGH".equals(risk.getRiskLevel()))
                ? "HIGH"
                : risks.stream().anyMatch(risk -> "MEDIUM".equals(risk.getRiskLevel())) ? "MEDIUM" : "LOW";
    }

    private String finalConclusion(ContractReview review, List<ClauseRisk> risks) {
        if (AgentTaskStatus.FAILED.name().equals(review.getStatus())) {
            return review.getFailureReason();
        }
        if (!AgentTaskStatus.COMPLETED.name().equals(review.getStatus())) {
            return "审查任务尚未完成，报告仍在生成或等待处理。";
        }
        if (risks.stream().anyMatch(risk -> "HIGH".equals(risk.getRiskLevel()))) {
            return "审查发现高风险条款，建议完成法务复核和合同修改后再签署。";
        }
        if (risks.stream().anyMatch(risk -> "MEDIUM".equals(risk.getRiskLevel()))) {
            return "审查发现中等风险条款，建议业务经办人与法务确认修改方案。";
        }
        return "审查未发现重大风险，可按内部流程继续推进。";
    }

    public record ReviewReport(ContractReview review, Contract contract, List<ClauseRisk> risks,
                               List<ApprovalRequest> approvals, String summary, String finalConclusion) {
    }

    public record ReviewTrace(ContractReview review, List<AgentStep> steps, List<AgentStateTransitionLog> transitions,
                              List<LlmCallLog> llmCalls, List<ToolCallLog> toolCalls,
                              List<RetrievalLog> retrievalLogs, TraceMetrics metrics) {
    }

    public record TraceMetrics(int stepCount, int llmCallCount, int toolCallCount, int retrievalCount,
                               int totalTokens, long totalLatencyMs, long stepDurationMs, long wallClockMs,
                               long toolLatencyMs, long llmLatencyMs, long retrievalLatencyMs) {
    }

    private long stepDurationMs(AgentStep step, OffsetDateTime now) {
        if (step.getStartedAt() == null) {
            return 0;
        }
        OffsetDateTime finishedAt = step.getFinishedAt() == null ? now : step.getFinishedAt();
        return Math.max(0, Duration.between(step.getStartedAt(), finishedAt).toMillis());
    }

    private long wallClockMs(List<AgentStep> steps, OffsetDateTime now) {
        OffsetDateTime firstStart = null;
        OffsetDateTime lastFinish = null;
        for (AgentStep step : steps) {
            if (step.getStartedAt() != null && (firstStart == null || step.getStartedAt().isBefore(firstStart))) {
                firstStart = step.getStartedAt();
            }
            OffsetDateTime finish = step.getFinishedAt() == null ? now : step.getFinishedAt();
            if (step.getStartedAt() != null && (lastFinish == null || finish.isAfter(lastFinish))) {
                lastFinish = finish;
            }
        }
        if (firstStart == null || lastFinish == null) {
            return 0;
        }
        return Math.max(0, Duration.between(firstStart, lastFinish).toMillis());
    }

    private void fail(ContractReview review, String reason, Long userId) {
        ContractReview current = requireById(review.getId());
        current.setFailureReason(reason);
        current.setUpdatedBy(userId);
        current.setUpdatedAt(OffsetDateTime.now());
        reviewMapper.updateById(current);
        progressRecorder.transition(review.getId(), AgentTaskStatus.FAILED, reason, userId, 100);
    }

    private void logTransition(Long reviewId, String from, String to, String reason, Long userId) {
        AgentStateTransitionLog log = new AgentStateTransitionLog();
        log.setReviewId(reviewId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setReason(reason);
        log.setCreatedBy(userId);
        transitionLogMapper.insert(log);
    }

    private List<RetrievedChunk> retrieveThemeReferences(Long reviewId, List<ThemeRetrievalQuery> themeQueries, CurrentUser user) {
        Map<String, RetrievedChunk> deduped = new LinkedHashMap<>();
        for (ThemeRetrievalQuery themeQuery : themeQueries) {
            List<RetrievedChunk> chunks = retrievalService.retrieve(reviewId, themeQuery.query(), 4, user);
            for (RetrievedChunk chunk : chunks) {
                String key = chunk.chunkId() == null ? chunk.documentId() + ":" + chunk.content() : String.valueOf(chunk.chunkId());
                deduped.putIfAbsent(key, chunk);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    static String buildRetrievalQuery(Contract contract, List<ContractClause> clauses) {
        if (clauses == null || clauses.isEmpty()) {
            return contract.getParsedText();
        }
        StringBuilder query = new StringBuilder("合同审查规则检索");
        appendLine(query, "合同类型", contract.getContractType());
        appendLine(query, "合同金额", contract.getContractAmount() == null ? null : contract.getContractAmount().toPlainString());
        appendLine(query, "客户", contract.getCustomerName());

        query.append("\n重点条款类型:\n");
        clauses.stream()
                .limit(12)
                .forEach(clause -> query.append("- ")
                        .append(clause.getClauseType())
                        .append(": ")
                        .append(clause.getClauseName())
                        .append("\n"));

        query.append("\n检索目标:\n");
        retrievalTargets(clauses).forEach(target -> query.append("- ").append(target).append("\n"));
        return query.toString().trim();
    }

    static List<ClauseInsight> buildClauseInsights(List<ContractClause> clauses) {
        if (clauses == null || clauses.isEmpty()) {
            return List.of();
        }
        return clauses.stream()
                .map(ContractReviewService::buildClauseInsight)
                .toList();
    }

    List<ClauseInsight> buildClauseInsights(Long reviewId, List<ContractClause> clauses, CurrentUser user) {
        List<ClauseInsight> deterministic = buildClauseInsights(clauses);
        if (deterministic.isEmpty()) {
            return deterministic;
        }
        List<ClauseInsight> enriched = new ArrayList<>();
        for (ClauseInsight base : deterministic) {
            enriched.add(enrichClauseInsightWithLlm(reviewId, base, user));
        }
        return enriched;
    }

    private ClauseInsight enrichClauseInsightWithLlm(Long reviewId, ClauseInsight base, CurrentUser user) {
        Instant start = Instant.now();
        String prompt = clauseInsightPrompt(base);
        try {
            StructuredOutputResponse response = llmGateway.structuredOutput(new StructuredOutputRequest(
                    new ChatRequest("clause-insight", null, List.of(
                            new ChatMessage(ChatMessage.Role.SYSTEM,
                                    "你是合同条款结构化抽取助手。只基于输入条款输出 JSON，不要编造事实；缺失信息返回空数组。"),
                            new ChatMessage(ChatMessage.Role.USER, prompt)
                    ), Map.of("temperature", 0)),
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "clauseType", Map.of("type", "string"),
                                    "clauseTypeLabel", Map.of("type", "string"),
                                    "summary", Map.of("type", "string"),
                                    "keyFacts", Map.of("type", "array", "items", Map.of("type", "string")),
                                    "riskSignals", Map.of("type", "array", "items", Map.of("type", "string")),
                                    "suggestedRiskLevel", Map.of("type", "string"),
                                    "confidence", Map.of("type", "number")
                            )
                    )
            ));
            ClauseInsight insight = mergeClauseInsight(base, response.data());
            logClauseInsightLlmCall(reviewId, base, prompt, response, Duration.between(start, Instant.now()).toMillis(),
                    true, null, user.id());
            return insight;
        } catch (RuntimeException ex) {
            logClauseInsightLlmCall(reviewId, base, prompt, null, Duration.between(start, Instant.now()).toMillis(),
                    false, ex.getMessage(), user.id());
            return base;
        }
    }

    private ClauseInsight mergeClauseInsight(ClauseInsight base, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return base;
        }
        String summary = data.get("summary") instanceof String value && !value.isBlank() ? value : base.summary();
        List<String> keyFacts = strings(data.get("keyFacts"));
        List<String> riskSignals = strings(data.get("riskSignals"));
        String clauseType = data.get("clauseType") instanceof String value && !value.isBlank() ? value : base.clauseType();
        String clauseTypeLabel = data.get("clauseTypeLabel") instanceof String value && !value.isBlank()
                ? value
                : clauseTypeLabel(clauseType);
        String suggestedRiskLevel = data.get("suggestedRiskLevel") instanceof String value && !value.isBlank()
                ? normalizeRiskLevel(value)
                : base.suggestedRiskLevel();
        double confidence = data.get("confidence") instanceof Number number ? number.doubleValue() : base.confidence();
        return new ClauseInsight(
                base.clauseName(),
                clauseType,
                clauseTypeLabel,
                summary,
                keyFacts.isEmpty() ? base.keyFacts() : keyFacts,
                riskSignals.isEmpty() ? base.riskSignals() : riskSignals,
                base.evidence(),
                suggestedRiskLevel,
                confidence
        );
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String clauseInsightPrompt(ClauseInsight insight) {
        return "请对以下合同条款做结构化抽取，输出 summary、keyFacts、riskSignals。\n"
                + "clauseType: " + insight.clauseType() + "\n"
                + "clauseTypeLabel: " + insight.clauseTypeLabel() + "\n"
                + "clauseName: " + insight.clauseName() + "\n"
                + "deterministicFacts: " + insight.keyFacts() + "\n"
                + "deterministicSignals: " + insight.riskSignals() + "\n"
                + "clauseText:\n" + insight.evidence() + "\n"
                + "杈撳嚭瀛楁: clauseType, clauseTypeLabel, summary, keyFacts, riskSignals, suggestedRiskLevel(LOW/MEDIUM/HIGH), confidence(0-1).";
    }

    private void logClauseInsightLlmCall(Long reviewId, ClauseInsight insight, String prompt,
                                         StructuredOutputResponse response, long latencyMs,
                                         boolean success, String errorMessage, Long userId) {
        if (llmCallLogMapper == null) {
            return;
        }
        TokenUsage usage = response == null ? null : response.usage();
        LlmCallLog log = new LlmCallLog();
        log.setReviewId(reviewId);
        log.setProvider(response == null ? null : response.provider());
        log.setModelName(response == null ? null : response.model());
        log.setPromptVersion("clause-insight");
        log.setRequestBody("{\"scenario\":\"clause-insight\",\"clauseType\":"
                + JsonStrings.quote(insight.clauseType()) + ",\"prompt\":" + JsonStrings.quote(prompt) + "}");
        log.setResponseBody("{\"data\":" + JsonStrings.quote(response == null ? null : String.valueOf(response.data())) + "}");
        log.setPromptTokens(usage == null ? null : usage.promptTokens());
        log.setCompletionTokens(usage == null ? null : usage.completionTokens());
        log.setTotalTokens(usage == null ? null : usage.totalTokens());
        log.setLatencyMs(latencyMs);
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setCreatedBy(userId);
        llmCallLogMapper.insert(log);
    }

    private static ClauseInsight buildClauseInsight(ContractClause clause) {
        String text = safe(clause.getClauseText());
        String type = safe(clause.getClauseType());
        String label = clauseTypeLabel(type);
        List<String> keyFacts = extractKeyFacts(type, text);
        List<String> riskSignals = extractRiskSignals(type, text);
        String summary = summarizeClause(clause.getClauseName(), text);
        return new ClauseInsight(
                clause.getClauseName(),
                type,
                label,
                summary,
                keyFacts,
                riskSignals,
                evidenceSnippet(text),
                suggestedRiskLevel(riskSignals),
                riskSignals.isEmpty() ? 0.4 : 0.7
        );
    }

    static List<ThemeRetrievalQuery> buildThemeRetrievalQueries(Contract contract, List<ClauseInsight> insights) {
        if (insights == null || insights.isEmpty()) {
            return List.of(new ThemeRetrievalQuery("综合审查", buildFallbackRetrievalQuery(contract)));
        }

        Map<String, List<ClauseInsight>> groups = new LinkedHashMap<>();
        for (ClauseInsight insight : insights) {
            String theme = themeForClauseType(insight.clauseType());
            groups.computeIfAbsent(theme, ignored -> new ArrayList<>()).add(insight);
        }

        List<ThemeRetrievalQuery> queries = new ArrayList<>();
        for (Map.Entry<String, List<ClauseInsight>> entry : groups.entrySet()) {
            queries.add(new ThemeRetrievalQuery(entry.getKey(), buildThemeRetrievalQuery(contract, entry.getKey(), entry.getValue())));
        }
        return queries;
    }

    private static String buildThemeRetrievalQuery(Contract contract, String theme, List<ClauseInsight> insights) {
        StringBuilder query = new StringBuilder("合同审查主题 RAG 检索\n");
        query.append("主题: ").append(theme).append("\n");
        appendLine(query, "合同类型", contract.getContractType());
        appendLine(query, "合同金额", contract.getContractAmount() == null ? null : contract.getContractAmount().toPlainString());
        appendLine(query, "客户", contract.getCustomerName());
        query.append("\n条款摘要/事实/信号:\n");
        for (ClauseInsight insight : insights) {
            query.append("- ").append(insight.clauseTypeLabel())
                    .append(" | ").append(insight.clauseName())
                    .append(" | 摘要: ").append(insight.summary());
            if (!insight.keyFacts().isEmpty()) {
                query.append(" | 事实: ").append(String.join("; ", insight.keyFacts()));
            }
            if (!insight.riskSignals().isEmpty()) {
                query.append(" | 信号: ").append(String.join("; ", insight.riskSignals()));
            }
            query.append("\n");
        }
        query.append("\n检索目标:\n");
        retrievalTargetsFromInsights(insights).forEach(target -> query.append("- ").append(target).append("\n"));
        return query.toString().trim();
    }

    private static String buildFallbackRetrievalQuery(Contract contract) {
        StringBuilder query = new StringBuilder("合同审查规则检索");
        appendLine(query, "合同类型", contract.getContractType());
        appendLine(query, "合同金额", contract.getContractAmount() == null ? null : contract.getContractAmount().toPlainString());
        appendLine(query, "客户", contract.getCustomerName());
        return query.toString().trim();
    }

    private static List<String> extractKeyFacts(String type, String text) {
        List<String> facts = new ArrayList<>();
        if ("PAYMENT_TERM".equals(type) || "ACCEPTANCE".equals(type) || "NOTICE".equals(type)) {
            Matcher matcher = DAYS_PATTERN.matcher(text);
            if (matcher.find()) {
                String key = "PAYMENT_TERM".equals(type) ? "paymentDays" : "days";
                facts.add(key + "=" + matcher.group(1));
            }
        }
        if ("AMOUNT".equals(type)) {
            Matcher matcher = AMOUNT_PATTERN.matcher(text);
            if (matcher.find()) {
                facts.add("amount=" + matcher.group(1).replace(",", ""));
                if (matcher.group(2) != null) {
                    facts.add("unit=" + matcher.group(2));
                }
            }
        }
        return facts;
    }

    private static List<String> extractRiskSignals(String type, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> signals = new ArrayList<>();
        if (("LIABILITY".equals(type) || "LIABILITY_CAP".equals(type))
                && containsAny(lower, "无限", "不设上限", "unlimited", "no cap")) {
            signals.add("可能存在无限责任或责任上限缺失");
        }
        if ("PAYMENT_TERM".equals(type)) {
            Matcher matcher = DAYS_PATTERN.matcher(text);
            if (matcher.find() && Integer.parseInt(matcher.group(1)) > 60) {
                signals.add("付款周期超过60日");
            }
        }
        if ("CONFIDENTIALITY".equals(type) && !containsAny(lower, "期限", "责任", "duration", "liability")) {
            signals.add("保密期限或违约责任可能不完整");
        }
        return signals;
    }

    private static String summarizeClause(String clauseName, String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return safe(clauseName);
        }
        int end = firstPositive(normalized.indexOf('。'), normalized.indexOf('.'), normalized.indexOf(';'), normalized.indexOf('；'));
        String sentence = end >= 0 ? normalized.substring(0, end + 1) : normalized;
        return sentence.length() > 90 ? sentence.substring(0, 90) : sentence;
    }

    private static int firstPositive(int... indexes) {
        int result = -1;
        for (int index : indexes) {
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private static String evidenceSnippet(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

    private static String clauseTypeLabel(String type) {
        return switch (type) {
            case "PARTIES" -> "合同主体";
            case "AMOUNT" -> "合同金额";
            case "PAYMENT_TERM" -> "付款条款";
            case "TERM" -> "合同期限";
            case "LIABILITY" -> "违约责任";
            case "LIABILITY_CAP" -> "责任上限";
            case "CONFIDENTIALITY" -> "保密条款";
            case "INTELLECTUAL_PROPERTY" -> "知识产权";
            case "DATA_PROTECTION" -> "数据保护";
            case "DISPUTE_RESOLUTION" -> "争议解决";
            case "TERMINATION" -> "终止解除";
            case "AUTO_RENEWAL" -> "自动续约";
            case "ACCEPTANCE" -> "验收交付";
            case "NOTICE" -> "通知条款";
            default -> "其他条款";
        };
    }

    private static String suggestedRiskLevel(List<String> riskSignals) {
        return riskSignals == null || riskSignals.isEmpty() ? "LOW" : "MEDIUM";
    }

    private static String normalizeRiskLevel(String value) {
        return List.of("LOW", "MEDIUM", "HIGH").contains(value) ? value : "MEDIUM";
    }

    private static String themeForClauseType(String type) {
        return switch (type) {
            case "AMOUNT", "PAYMENT_TERM" -> "付款与金额";
            case "LIABILITY", "LIABILITY_CAP" -> "责任与违约";
            case "ACCEPTANCE" -> "交付与验收";
            case "TERMINATION", "AUTO_RENEWAL", "NOTICE", "TERM" -> "期限终止与通知";
            case "CONFIDENTIALITY", "DATA_PROTECTION", "INTELLECTUAL_PROPERTY" -> "保密数据与知识产权";
            case "DISPUTE_RESOLUTION" -> "争议解决";
            default -> "综合审查";
        };
    }

    private static Set<String> retrievalTargetsFromInsights(List<ClauseInsight> insights) {
        Set<String> targets = new LinkedHashSet<>();
        for (ClauseInsight insight : insights) {
            switch (insight.clauseType()) {
                case "AMOUNT" -> targets.add("高金额合同审批要求、付款安排和责任规则");
                case "PAYMENT_TERM" -> targets.add("付款周期限制、账期、逾期付款和分阶段付款规则");
                case "LIABILITY", "LIABILITY_CAP" -> targets.add("违约责任、赔偿责任、责任上限和无限责任限制");
                case "CONFIDENTIALITY" -> targets.add("保密条款完整性、保密期限、例外披露和违约责任");
                case "DATA_PROTECTION" -> targets.add("数据保护、个人信息处理、隐私和安全合规要求");
                case "DISPUTE_RESOLUTION" -> targets.add("争议解决、仲裁、诉讼管辖和适用法律要求");
                case "TERMINATION" -> targets.add("合同解除、终止条件、通知期限和终止后义务");
                case "ACCEPTANCE" -> targets.add("验收标准、验收期限、拒收和整改流程");
                case "INTELLECTUAL_PROPERTY" -> targets.add("知识产权归属、许可、交付成果权利和侵权责任");
                case "AUTO_RENEWAL" -> targets.add("自动续约、续约通知、退出窗口和续约审批");
                case "NOTICE" -> targets.add("书面通知方式、送达规则和通知期限");
                default -> targets.add("合同主体、金额、付款、责任、保密、数据保护、验收、终止和争议解决合规要求");
            }
        }
        return targets;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append("\n").append(label).append(": ").append(value);
        }
    }

    private static Set<String> retrievalTargets(List<ContractClause> clauses) {
        Set<String> targets = new LinkedHashSet<>();
        for (ContractClause clause : clauses) {
            switch (clause.getClauseType()) {
                case "AMOUNT" -> targets.add("高金额合同审批要求、付款安排和责任规则");
                case "PAYMENT_TERM" -> targets.add("付款周期限制、账期、逾期付款和分阶段付款规则");
                case "LIABILITY", "LIABILITY_CAP" -> targets.add("违约责任、赔偿责任、责任上限和无限责任限制");
                case "CONFIDENTIALITY" -> targets.add("保密条款完整性、保密期限、例外披露和违约责任");
                case "DATA_PROTECTION" -> targets.add("数据保护、个人信息处理、隐私和安全合规要求");
                case "DISPUTE_RESOLUTION" -> targets.add("争议解决、仲裁、诉讼管辖和适用法律要求");
                case "TERMINATION" -> targets.add("合同解除、终止条件、通知期限和终止后义务");
                case "ACCEPTANCE" -> targets.add("验收标准、验收期限、拒收和整改流程");
                case "INTELLECTUAL_PROPERTY" -> targets.add("知识产权归属、许可、交付成果权利和侵权责任");
                case "AUTO_RENEWAL" -> targets.add("自动续约、续约通知、退出窗口和续约审批");
                case "NOTICE" -> targets.add("书面通知方式、送达规则和通知期限");
                default -> {
                }
            }
        }
        if (targets.isEmpty()) {
            targets.add("合同主体、金额、付款、责任、保密、数据保护、验收、终止和争议解决合规要求");
        }
        return targets;
    }

    private String clauseTypesJson(List<ContractClause> clauses) {
        return clauses.stream()
                .map(ContractClause::getClauseType)
                .distinct()
                .map(JsonStrings::quote)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String themesJson(List<ThemeRetrievalQuery> themeQueries) {
        return themeQueries.stream()
                .map(ThemeRetrievalQuery::theme)
                .distinct()
                .map(JsonStrings::quote)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private void logToolCall(Long reviewId, String toolName, String arguments, String result, boolean success,
                             String errorMessage, long latencyMs, Long userId) {
        ToolCallLog log = new ToolCallLog();
        log.setReviewId(reviewId);
        log.setToolName(toolName);
        log.setArguments(arguments == null ? "{}" : arguments);
        log.setResult(result == null ? "{}" : result);
        log.setPermissionResult("{\"allowed\":" + success + "}");
        log.setLatencyMs(latencyMs);
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setCreatedBy(userId);
        toolCallLogMapper.insert(log);
    }

    public record ClauseInsight(String clauseName, String clauseType, String clauseTypeLabel, String summary,
                                List<String> keyFacts, List<String> riskSignals, String evidence,
                                String suggestedRiskLevel, double confidence) {
        public ClauseInsight(String clauseName, String clauseType, String clauseTypeLabel, String summary,
                             List<String> keyFacts, List<String> riskSignals, String evidence) {
            this(clauseName, clauseType, clauseTypeLabel, summary, keyFacts, riskSignals, evidence,
                    ContractReviewService.suggestedRiskLevel(riskSignals),
                    riskSignals == null || riskSignals.isEmpty() ? 0.4 : 0.7);
        }
    }

    public record ThemeRetrievalQuery(String theme, String query) {
    }
}
