package com.example.lexiflow.prompt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.prompt.mapper.PromptTemplateMapper;
import com.example.lexiflow.prompt.model.PromptTemplate;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptTemplateServiceTest {

    @Mock
    private PromptTemplateMapper mapper;

    private PromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService(mapper);
    }

    // === rendering ===

    @Test
    void rendersTemplateWithVariables() {
        PromptTemplate template = template("合同审查: {{contract_name}}", "clause-extraction", "v1");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(template));

        var result = service.render("clause-extraction", Map.of("contract_name", "采购合同2025"));

        Assertions.assertThat(result.content()).isEqualTo("合同审查: 采购合同2025");
    }

    @Test
    void rendersMultipleVariables() {
        PromptTemplate template = template("{{party_a}}与{{party_b}}的{{agreement_type}}", "qa", "v1");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(template));

        var result = service.render("qa", Map.of("party_a", "甲方", "party_b", "乙方", "agreement_type", "采购协议"));

        Assertions.assertThat(result.content()).isEqualTo("甲方与乙方的采购协议");
    }

    @Test
    void unmatchedVariablesBecomeEmpty() {
        PromptTemplate template = template("合同名称: {{contract_name}}, 金额: {{amount}}", "risk", "v1");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(template));

        var result = service.render("risk", Map.of("contract_name", "测试合同"));

        Assertions.assertThat(result.content()).startsWith("合同名称: 测试合同");
    }

    @Test
    void handlesEmptyVariables() {
        PromptTemplate template = template("标准模板无变量", "report", "v1");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(template));

        var result = service.render("report", Map.of());

        Assertions.assertThat(result.content()).isEqualTo("标准模板无变量");
    }

    @Test
    void usesNewestEnabledTemplateForScene() {
        PromptTemplate oldTemplate = template("旧模板", "clause-extraction", "v1");
        PromptTemplate newTemplate = template("新模板", "clause-extraction", "v2");
        newTemplate.setCreatedAt(java.time.OffsetDateTime.now().plusDays(1));
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(newTemplate, oldTemplate));

        var result = service.render("clause-extraction", Map.of());

        Assertions.assertThat(result.content()).isEqualTo("新模板");
    }

    @Test
    void fallsBackToBuiltinWhenNoEnabledTemplate() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var result = service.render("clause-extraction", Map.of("input", "测试合同全文"));

        Assertions.assertThat(result.content()).contains("测试合同全文");
        Assertions.assertThat(result.name()).isEqualTo("builtin-clause-extraction");
    }

    @Test
    void renderIncludesSceneAndVersionInResult() {
        PromptTemplate template = template("内容", "risk-analysis", "v3");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(template));

        var result = service.render("risk-analysis", Map.of());

        Assertions.assertThat(result.scene()).isEqualTo("risk-analysis");
        Assertions.assertThat(result.version()).isEqualTo("v3");
    }

    // === requireById ===

    @Test
    void requireByIdReturnsExistingTemplate() {
        PromptTemplate template = template("内容", "qa", "v1");
        template.setId(42L);
        when(mapper.selectById(42L)).thenReturn(template);

        Assertions.assertThat(service.requireById(42L).getId()).isEqualTo(42L);
    }

    @Test
    void requireByIdThrowsWhenNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);

        Assertions.assertThatThrownBy(() -> service.requireById(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireByIdThrowsWhenSoftDeleted() {
        PromptTemplate deleted = template("内容", "qa", "v1");
        deleted.setDeleted(true);
        when(mapper.selectById(1L)).thenReturn(deleted);

        Assertions.assertThatThrownBy(() -> service.requireById(1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // === helpers ===

    private PromptTemplate template(String content, String scene, String version) {
        PromptTemplate t = new PromptTemplate();
        t.setId(1L);
        t.setName("test-template");
        t.setTemplateContent(content);
        t.setScene(scene);
        t.setVersion(version);
        t.setVariables("[]");
        t.setOutputConstraints("{}");
        t.setEnabled(true);
        t.setDeleted(false);
        return t;
    }
}
