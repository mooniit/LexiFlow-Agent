package com.example.lexiflow.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolPermissionGuard {

    private final ReviewToolConfigMapper toolConfigMapper;

    public ToolPermissionGuard(ReviewToolConfigMapper toolConfigMapper) {
        this.toolConfigMapper = toolConfigMapper;
    }

    public ReviewToolConfig requireAllowed(String toolName, CurrentUser user) {
        ReviewToolConfig config = toolConfigMapper.selectOne(new LambdaQueryWrapper<ReviewToolConfig>()
                .eq(ReviewToolConfig::getToolName, toolName)
                .eq(ReviewToolConfig::getDeleted, false));
        if (config == null) {
            throw new ToolPermissionDeniedException("Tool is not registered: " + toolName);
        }
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new ToolPermissionDeniedException("Tool is disabled: " + toolName);
        }
        String requiredPermission = config.getRequiredPermission();
        if (StringUtils.hasText(requiredPermission) && !user.permissions().contains(requiredPermission)) {
            throw new ToolPermissionDeniedException("Missing permission [" + requiredPermission + "] for tool: " + toolName);
        }
        if (Boolean.TRUE.equals(config.getApprovalRequired()) && !user.permissions().contains("approval:write")) {
            throw new ToolPermissionDeniedException("Tool requires approval permission: " + toolName);
        }
        return config;
    }
}

