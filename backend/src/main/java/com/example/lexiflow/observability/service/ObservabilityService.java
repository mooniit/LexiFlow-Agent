package com.example.lexiflow.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.approval.mapper.ApprovalRequestMapper;
import com.example.lexiflow.approval.model.ApprovalRequest;
import com.example.lexiflow.contract.mapper.ContractMapper;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.review.mapper.ContractReviewMapper;
import com.example.lexiflow.review.model.ContractReview;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import com.example.lexiflow.user.mapper.AppUserMapper;
import com.example.lexiflow.user.model.AppUser;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ObservabilityService {

    private final MeterRegistry meterRegistry;
    private final ContractMapper contractMapper;
    private final ContractReviewMapper reviewMapper;
    private final ApprovalRequestMapper approvalMapper;
    private final ReviewToolConfigMapper toolConfigMapper;
    private final AppUserMapper userMapper;

    public ObservabilityService(MeterRegistry meterRegistry, ContractMapper contractMapper,
                                ContractReviewMapper reviewMapper, ApprovalRequestMapper approvalMapper,
                                ReviewToolConfigMapper toolConfigMapper, AppUserMapper userMapper) {
        this.meterRegistry = meterRegistry;
        this.contractMapper = contractMapper;
        this.reviewMapper = reviewMapper;
        this.approvalMapper = approvalMapper;
        this.toolConfigMapper = toolConfigMapper;
        this.userMapper = userMapper;
    }

    public ObservabilitySummary summary(CurrentUser user) {
        requireAdmin(user);
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        return new ObservabilitySummary(
                "UP",
                OffsetDateTime.now(),
                Map.of(
                        "contracts", activeContracts(),
                        "reviews", activeReviews(),
                        "pendingApprovals", pendingApprovals(),
                        "enabledTools", enabledTools(),
                        "activeUsers", activeUsers()
                ),
                Map.of(
                        "created", reviewsByStatus("CREATED"),
                        "waitingApproval", reviewsByStatus("WAITING_APPROVAL"),
                        "completed", reviewsByStatus("COMPLETED"),
                        "failed", reviewsByStatus("FAILED"),
                        "cancelled", reviewsByStatus("CANCELLED")
                ),
                new RuntimeSummary(
                        ManagementFactory.getRuntimeMXBean().getUptime(),
                        usedMemory,
                        maxMemory,
                        meterGauge("jvm.threads.live"),
                        meterGauge("system.cpu.usage"),
                        meterGauge("process.cpu.usage")
                ),
                Map.of(
                        "health", "/api/actuator/health",
                        "metrics", "/api/actuator/metrics",
                        "info", "/api/actuator/info"
                )
        );
    }

    private long activeContracts() {
        return contractMapper.selectCount(new LambdaQueryWrapper<Contract>().eq(Contract::getDeleted, false));
    }

    private long activeReviews() {
        return reviewMapper.selectCount(new LambdaQueryWrapper<ContractReview>().eq(ContractReview::getDeleted, false));
    }

    private long pendingApprovals() {
        return approvalMapper.selectCount(new LambdaQueryWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getDeleted, false)
                .eq(ApprovalRequest::getStatus, "PENDING"));
    }

    private long enabledTools() {
        return toolConfigMapper.selectCount(new LambdaQueryWrapper<ReviewToolConfig>()
                .eq(ReviewToolConfig::getDeleted, false)
                .eq(ReviewToolConfig::getEnabled, true));
    }

    private long activeUsers() {
        return userMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getDeleted, false)
                .eq(AppUser::getEnabled, true));
    }

    private long reviewsByStatus(String status) {
        return reviewMapper.selectCount(new LambdaQueryWrapper<ContractReview>()
                .eq(ContractReview::getDeleted, false)
                .eq(ContractReview::getStatus, status));
    }

    private Double meterGauge(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge == null ? null : gauge.value();
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.permissions().contains("admin:manage")) {
            throw new IllegalStateException("Admin permission is required.");
        }
    }

    public record ObservabilitySummary(String status, OffsetDateTime generatedAt, Map<String, Long> counters,
                                       Map<String, Long> reviewStatuses, RuntimeSummary runtime,
                                       Map<String, String> actuatorLinks) {
    }

    public record RuntimeSummary(long uptimeMs, long usedMemoryBytes, long maxMemoryBytes, Double liveThreads,
                                 Double systemCpuUsage, Double processCpuUsage) {
    }
}
