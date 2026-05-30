package com.example.lexiflow.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.approval.service.ApprovalService;
import com.example.lexiflow.approval.service.ApprovalEventMessage;
import com.example.lexiflow.agent.model.AgentStepType;
import com.example.lexiflow.agent.model.AgentTaskStatus;
import com.example.lexiflow.agent.service.AgentStateMachine;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractStatus;
import com.example.lexiflow.contract.service.ClauseExtractionService;
import com.example.lexiflow.contract.service.ContractService;
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
import com.example.lexiflow.user.mapper.AppUserMapper;
import com.example.lexiflow.user.mapper.UserPermissionMapper;
import com.example.lexiflow.user.model.AppUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ContractReviewService {

    private final ContractReviewMapper reviewMapper;
    private final AgentStepMapper stepMapper;
    private final AgentStateTransitionLogMapper transitionLogMapper;
    private final ContractService contractService;
    private final ClauseExtractionService clauseExtractionService;
    private final RagRetrievalService retrievalService;
    private final RiskAnalysisService riskAnalysisService;
    private final AgentStateMachine stateMachine;
    private final ReviewEventBus eventBus;
    private final ReviewJobPublisher reviewJobPublisher;
    private final AppUserMapper userMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final ApprovalService approvalService;

    public ContractReviewService(ContractReviewMapper reviewMapper, AgentStepMapper stepMapper,
                                 AgentStateTransitionLogMapper transitionLogMapper, ContractService contractService,
                                 ClauseExtractionService clauseExtractionService, RagRetrievalService retrievalService,
                                 RiskAnalysisService riskAnalysisService, AgentStateMachine stateMachine,
                                 ReviewEventBus eventBus, ReviewJobPublisher reviewJobPublisher,
                                 AppUserMapper userMapper, UserPermissionMapper userPermissionMapper,
                                 ApprovalService approvalService) {
        this.reviewMapper = reviewMapper;
        this.stepMapper = stepMapper;
        this.transitionLogMapper = transitionLogMapper;
        this.contractService = contractService;
        this.clauseExtractionService = clauseExtractionService;
        this.retrievalService = retrievalService;
        this.riskAnalysisService = riskAnalysisService;
        this.stateMachine = stateMachine;
        this.eventBus = eventBus;
        this.reviewJobPublisher = reviewJobPublisher;
        this.userMapper = userMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.approvalService = approvalService;
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
        ContractReview review = requireById(reviewId);
        transition(review, AgentTaskStatus.CANCELLED, "Review cancelled by user.", user.id(), 100);
        return review;
    }

    @Transactional
    public ContractReview rerun(Long reviewId, CurrentUser user) {
        ContractReview review = requireById(reviewId);
        if (!AgentTaskStatus.FAILED.name().equals(review.getStatus())) {
            throw new IllegalArgumentException("Only failed review tasks can be rerun.");
        }
        transition(review, AgentTaskStatus.PARSING, "Review rerun requested.", user.id(), 10);
        publishAfterCommit(reviewId, user);
        return requireById(reviewId);
    }

    @Transactional
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
        ContractReview review = requireById(reviewId);
        transition(review, AgentTaskStatus.PARSING, "Start contract parsing.", user.id(), 10);
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
        try {
            addStep(review.getId(), AgentStepType.CONTRACT_PARSE, "RUNNING", "{\"contractId\":" + review.getContractId() + "}", "{}", null);
            Contract contract = contractService.parse(review.getContractId(), user);
            if (!ContractStatus.PARSED.name().equals(contract.getStatus())) {
                fail(review, "Contract parsing failed. Check contract metadata for parse_failure.", user.id());
                return;
            }
            finishLastRunningStep(review.getId(), "{\"textLength\":" + contract.getParsedText().length() + "}");

            transition(review, AgentTaskStatus.EXTRACTING, "Extract contract clauses.", user.id(), 30);
            List<ContractClause> clauses = clauseExtractionService.extract(contract, user);
            addStep(review.getId(), AgentStepType.CLAUSE_EXTRACTION, "COMPLETED", "{}", "{\"clauseCount\":" + clauses.size() + "}", null);
            eventBus.publish(review.getId(), "CLAUSE_EXTRACTION", "Clause extraction completed.");

            transition(review, AgentTaskStatus.RETRIEVING_RULES, "Retrieve compliance rules.", user.id(), 50);
            String retrievalQuery = clauses.isEmpty() ? contract.getParsedText() : clauses.stream()
                    .map(ContractClause::getClauseText)
                    .limit(5)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse(contract.getParsedText());
            List<RetrievedChunk> references = retrievalService.retrieve(review.getId(), retrievalQuery, 5, user);
            addStep(review.getId(), AgentStepType.RULE_RETRIEVAL, "COMPLETED", "{}", "{\"matchedRules\":" + references.size() + "}", null);
            eventBus.publish(review.getId(), "RULE_RETRIEVAL", "Rule retrieval completed.");

            transition(review, AgentTaskStatus.ANALYZING, "Analyze contract risks.", user.id(), 70);
            var risks = riskAnalysisService.analyze(review.getId(), review.getContractId(), clauses, references, user);
            addStep(review.getId(), AgentStepType.RISK_ANALYSIS, "COMPLETED", "{}", "{\"riskCount\":" + risks.size() + "}", null);
            eventBus.publish(review.getId(), "RISK_ANALYSIS", "Risk analysis completed.");

            if (requiresApproval(risks)) {
                approvalService.ensurePendingReviewApproval(review.getId(), risks, user);
                review.setOverallRiskLevel(overallRisk(risks));
                review.setResultSummary("{\"summary\":\"High-risk review is waiting for manual approval.\",\"clauseCount\":"
                        + clauses.size() + ",\"riskCount\":" + risks.size() + ",\"overallRiskLevel\":\"" + overallRisk(risks)
                        + "\",\"approvalRequired\":true}");
                reviewMapper.updateById(review);
                transition(review, AgentTaskStatus.WAITING_APPROVAL, "High-risk review requires manual approval.", user.id(), 80);
                return;
            }
            generateReport(review, clauses.size(), risks, user, "Automated review completed.", false);
        } catch (RuntimeException ex) {
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
        List<ClauseRisk> risks = riskAnalysisService.listByReview(reviewId);
        return new ReviewReport(review, risks, review.getResultSummary());
    }

    public ReviewTrace trace(Long reviewId) {
        ContractReview review = requireById(reviewId);
        List<AgentStep> steps = steps(reviewId);
        List<AgentStateTransitionLog> transitions = transitionLogMapper.selectList(
                new LambdaQueryWrapper<AgentStateTransitionLog>()
                        .eq(AgentStateTransitionLog::getReviewId, reviewId)
                        .orderByAsc(AgentStateTransitionLog::getCreatedAt));
        return new ReviewTrace(review, steps, transitions);
    }

    private void generateReport(ContractReview review, int clauseCount, List<ClauseRisk> risks, CurrentUser user,
                                String summary, boolean approvalPassed) {
        transition(review, AgentTaskStatus.GENERATING_REPORT, "Generate review report.", user.id(), 90);
        String overallRisk = overallRisk(risks);
        addStep(review.getId(), AgentStepType.REPORT_GENERATION, "COMPLETED", "{}",
                "{\"summary\":\"" + summary + "\"}", null);
        review.setOverallRiskLevel(overallRisk);
        review.setResultSummary("{\"summary\":\"" + summary + "\",\"clauseCount\":"
                + clauseCount + ",\"riskCount\":" + risks.size() + ",\"overallRiskLevel\":\"" + overallRisk
                + "\",\"approvalRequired\":" + requiresApproval(risks) + ",\"approvalPassed\":" + approvalPassed + "}");
        transition(review, AgentTaskStatus.COMPLETED, "Review completed.", user.id(), 100);
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

    public record ReviewReport(ContractReview review, List<ClauseRisk> risks, String summary) {
    }

    public record ReviewTrace(ContractReview review, List<AgentStep> steps, List<AgentStateTransitionLog> transitions) {
    }

    private void fail(ContractReview review, String reason, Long userId) {
        review.setFailureReason(reason);
        transition(review, AgentTaskStatus.FAILED, reason, userId, 100);
        eventBus.publish(review.getId(), "FAILED", reason);
    }

    private void transition(ContractReview review, AgentTaskStatus to, String reason, Long userId, int progress) {
        AgentTaskStatus from = AgentTaskStatus.valueOf(review.getStatus());
        stateMachine.assertCanTransit(from, to);
        review.setStatus(to.name());
        review.setProgressPercent(progress);
        review.setUpdatedBy(userId);
        reviewMapper.updateById(review);
        logTransition(review.getId(), from.name(), to.name(), reason, userId);
        eventBus.publish(review.getId(), to.name(), reason);
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

    private void addStep(Long reviewId, AgentStepType stepType, String status, String input, String output, String error) {
        AgentStep step = new AgentStep();
        step.setReviewId(reviewId);
        step.setStepType(stepType.name());
        step.setStatus(status);
        step.setInputSummary(input);
        step.setOutputSummary(output);
        step.setErrorMessage(error);
        step.setStartedAt(OffsetDateTime.now());
        if (!"RUNNING".equals(status)) {
            step.setFinishedAt(OffsetDateTime.now());
        }
        stepMapper.insert(step);
    }

    private void finishLastRunningStep(Long reviewId, String output) {
        List<AgentStep> runningSteps = stepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getReviewId, reviewId)
                .eq(AgentStep::getStatus, "RUNNING")
                .orderByDesc(AgentStep::getCreatedAt));
        if (!runningSteps.isEmpty()) {
            AgentStep step = runningSteps.getFirst();
            step.setStatus("COMPLETED");
            step.setOutputSummary(output);
            step.setFinishedAt(OffsetDateTime.now());
            stepMapper.updateById(step);
            eventBus.publish(reviewId, step.getStepType(), "Contract parsing completed.");
        }
    }
}
