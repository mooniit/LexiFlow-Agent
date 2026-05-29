package com.example.lexiflow.rag.service;

import com.example.lexiflow.rag.model.KnowledgeBase;
import com.example.lexiflow.security.CurrentUser;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeAccessGuard {

    public boolean canRead(KnowledgeBase base, CurrentUser user) {
        if (base == null || !"ACTIVE".equals(base.getStatus())) {
            return false;
        }
        String visibility = base.getVisibility() == null ? "PRIVATE" : base.getVisibility().toUpperCase(Locale.ROOT);
        if ("PUBLIC".equals(visibility)) {
            return true;
        }
        if ("DEPARTMENT".equals(visibility) && base.getDepartmentId() != null) {
            return base.getDepartmentId().equals(user.departmentId());
        }
        if (containsAnyRole(base.getAllowedRoles(), user)) {
            return true;
        }
        return base.getCreatedBy() != null && base.getCreatedBy().equals(user.id());
    }

    public void requireRead(KnowledgeBase base, CurrentUser user) {
        if (!canRead(base, user)) {
            throw new KnowledgeAccessDeniedException("No permission to read knowledge base: " + (base == null ? null : base.getId()));
        }
    }

    private boolean containsAnyRole(String allowedRolesJson, CurrentUser user) {
        if (!StringUtils.hasText(allowedRolesJson) || "[]".equals(allowedRolesJson.trim())) {
            return false;
        }
        return user.roles().stream().anyMatch(role -> allowedRolesJson.contains("\"" + role + "\""));
    }
}

