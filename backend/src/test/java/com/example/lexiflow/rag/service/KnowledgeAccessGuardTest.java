package com.example.lexiflow.rag.service;

import com.example.lexiflow.rag.model.KnowledgeBase;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class KnowledgeAccessGuardTest {

    private final KnowledgeAccessGuard guard = new KnowledgeAccessGuard();

    @Test
    void allowsPublicKnowledgeBase() {
        KnowledgeBase base = base("PUBLIC", null, "[]", 2L);
        CurrentUser user = user(1L, null, List.of("BUSINESS_USER"));

        Assertions.assertThat(guard.canRead(base, user)).isTrue();
    }

    @Test
    void allowsDepartmentKnowledgeBaseOnlyForSameDepartment() {
        KnowledgeBase base = base("DEPARTMENT", 10L, "[]", 2L);

        Assertions.assertThat(guard.canRead(base, user(1L, 10L, List.of("BUSINESS_USER")))).isTrue();
        Assertions.assertThat(guard.canRead(base, user(1L, 20L, List.of("BUSINESS_USER")))).isFalse();
    }

    @Test
    void allowsRoleOrOwnerAccessForPrivateKnowledgeBase() {
        KnowledgeBase base = base("PRIVATE", null, "[\"LEGAL_REVIEWER\"]", 9L);

        Assertions.assertThat(guard.canRead(base, user(1L, null, List.of("LEGAL_REVIEWER")))).isTrue();
        Assertions.assertThat(guard.canRead(base, user(9L, null, List.of("BUSINESS_USER")))).isTrue();
        Assertions.assertThat(guard.canRead(base, user(1L, null, List.of("BUSINESS_USER")))).isFalse();
    }

    private KnowledgeBase base(String visibility, Long departmentId, String allowedRoles, Long createdBy) {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(1L);
        base.setVisibility(visibility);
        base.setDepartmentId(departmentId);
        base.setAllowedRoles(allowedRoles);
        base.setStatus("ACTIVE");
        base.setCreatedBy(createdBy);
        return base;
    }

    private CurrentUser user(Long id, Long departmentId, List<String> roles) {
        return new CurrentUser(id, "user" + id, "User " + id, departmentId, roles, List.of("knowledge:read"), true);
    }
}

