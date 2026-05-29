package com.example.lexiflow.tool.service;

import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.mapper.ReviewToolConfigMapper;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ToolPermissionGuardTest {

    @Test
    void allowsEnabledToolWhenUserHasPermission() {
        ToolPermissionGuard guard = new ToolPermissionGuard(mapperReturning(config("risk_analysis", "tool:execute", true, false)));

        Assertions.assertThatCode(() -> guard.requireAllowed("risk_analysis", user(List.of("tool:execute"))))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksMissingPermission() {
        ToolPermissionGuard guard = new ToolPermissionGuard(mapperReturning(config("risk_analysis", "tool:execute", true, false)));

        Assertions.assertThatThrownBy(() -> guard.requireAllowed("risk_analysis", user(List.of("knowledge:read"))))
                .isInstanceOf(ToolPermissionDeniedException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void blocksDisabledTool() {
        ToolPermissionGuard guard = new ToolPermissionGuard(mapperReturning(config("risk_analysis", "tool:execute", false, false)));

        Assertions.assertThatThrownBy(() -> guard.requireAllowed("risk_analysis", user(List.of("tool:execute"))))
                .isInstanceOf(ToolPermissionDeniedException.class)
                .hasMessageContaining("disabled");
    }

    private ReviewToolConfig config(String name, String permission, boolean enabled, boolean approvalRequired) {
        ReviewToolConfig config = new ReviewToolConfig();
        config.setToolName(name);
        config.setRequiredPermission(permission);
        config.setEnabled(enabled);
        config.setApprovalRequired(approvalRequired);
        return config;
    }

    private CurrentUser user(List<String> permissions) {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), permissions, true);
    }

    private ReviewToolConfigMapper mapperReturning(ReviewToolConfig config) {
        ReviewToolConfigMapper mapper = Mockito.mock(ReviewToolConfigMapper.class);
        Mockito.when(mapper.selectOne(Mockito.any())).thenReturn(config);
        return mapper;
    }
}
