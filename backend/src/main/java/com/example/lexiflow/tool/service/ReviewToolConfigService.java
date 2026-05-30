package com.example.lexiflow.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReviewToolConfigService {

    private final ReviewToolConfigMapper toolConfigMapper;

    public ReviewToolConfigService(ReviewToolConfigMapper toolConfigMapper) {
        this.toolConfigMapper = toolConfigMapper;
    }

    public List<ReviewToolConfig> list(CurrentUser user) {
        requireAdmin(user);
        return toolConfigMapper.selectList(new LambdaQueryWrapper<ReviewToolConfig>()
                .eq(ReviewToolConfig::getDeleted, false)
                .orderByAsc(ReviewToolConfig::getToolName));
    }

    @Transactional
    public ReviewToolConfig create(SaveToolCommand command, CurrentUser user) {
        requireAdmin(user);
        if (!StringUtils.hasText(command.toolName())) {
            throw new IllegalArgumentException("Tool name is required.");
        }
        ReviewToolConfig existing = toolConfigMapper.selectOne(new LambdaQueryWrapper<ReviewToolConfig>()
                .eq(ReviewToolConfig::getToolName, command.toolName())
                .eq(ReviewToolConfig::getDeleted, false));
        if (existing != null) {
            throw new IllegalArgumentException("Tool already exists: " + command.toolName());
        }
        ReviewToolConfig config = new ReviewToolConfig();
        apply(config, command);
        config.setCreatedBy(user.id());
        config.setUpdatedBy(user.id());
        config.setDeleted(false);
        toolConfigMapper.insert(config);
        return toolConfigMapper.selectById(config.getId());
    }

    @Transactional
    public ReviewToolConfig update(Long id, SaveToolCommand command, CurrentUser user) {
        requireAdmin(user);
        ReviewToolConfig config = requireTool(id);
        apply(config, command);
        config.setUpdatedBy(user.id());
        toolConfigMapper.updateById(config);
        return toolConfigMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        requireAdmin(user);
        ReviewToolConfig config = requireTool(id);
        config.setDeleted(true);
        config.setEnabled(false);
        config.setUpdatedBy(user.id());
        toolConfigMapper.updateById(config);
    }

    private ReviewToolConfig requireTool(Long id) {
        ReviewToolConfig config = toolConfigMapper.selectById(id);
        if (config == null || Boolean.TRUE.equals(config.getDeleted())) {
            throw new IllegalArgumentException("Tool config not found: " + id);
        }
        return config;
    }

    private void apply(ReviewToolConfig config, SaveToolCommand command) {
        if (StringUtils.hasText(command.toolName())) {
            config.setToolName(command.toolName());
        }
        config.setRequiredPermission(StringUtils.hasText(command.requiredPermission()) ? command.requiredPermission() : "tool:execute");
        config.setRiskLevel(StringUtils.hasText(command.riskLevel()) ? command.riskLevel() : "LOW");
        config.setApprovalRequired(command.approvalRequired() != null && command.approvalRequired());
        config.setEnabled(command.enabled() == null || command.enabled());
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.permissions().contains("admin:manage")) {
            throw new IllegalStateException("Admin permission is required.");
        }
    }

    public record SaveToolCommand(String toolName, String requiredPermission, String riskLevel,
                                  Boolean approvalRequired, Boolean enabled) {
    }
}
