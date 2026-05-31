package com.example.lexiflow.prompt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.prompt.mapper.PromptTemplateMapper;
import com.example.lexiflow.prompt.model.PromptTemplate;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PromptTemplateService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final PromptTemplateMapper promptTemplateMapper;

    public PromptTemplateService(PromptTemplateMapper promptTemplateMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
    }

    public List<PromptTemplate> list(String scene, Boolean enabled, CurrentUser user) {
        requireAdmin(user);
        LambdaQueryWrapper<PromptTemplate> query = new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getDeleted, false)
                .orderByAsc(PromptTemplate::getScene)
                .orderByDesc(PromptTemplate::getCreatedAt);
        if (StringUtils.hasText(scene)) {
            query.eq(PromptTemplate::getScene, scene);
        }
        if (enabled != null) {
            query.eq(PromptTemplate::getEnabled, enabled);
        }
        return promptTemplateMapper.selectList(query);
    }

    public PromptTemplate requireById(Long id) {
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null || Boolean.TRUE.equals(template.getDeleted())) {
            throw new IllegalArgumentException("Prompt template not found: " + id);
        }
        return template;
    }

    public PromptTemplate resolveEnabled(String scene) {
        PromptTemplate template = promptTemplateMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getScene, scene)
                        .eq(PromptTemplate::getEnabled, true)
                        .eq(PromptTemplate::getDeleted, false)
                        .orderByDesc(PromptTemplate::getCreatedAt))
                .stream()
                .findFirst()
                .orElse(null);
        if (template != null) {
            return template;
        }
        return fallback(scene);
    }

    public RenderedPrompt render(String scene, Map<String, ?> variables) {
        PromptTemplate template = resolveEnabled(scene);
        String content = renderContent(template.getTemplateContent(), variables);
        return new RenderedPrompt(template.getName(), template.getVersion(), template.getScene(), content);
    }

    @Transactional
    public PromptTemplate create(UpsertPromptTemplateRequest request, CurrentUser user) {
        requireAdmin(user);
        PromptTemplate template = new PromptTemplate();
        apply(template, request);
        template.setCreatedBy(user.id());
        template.setUpdatedBy(user.id());
        promptTemplateMapper.insert(template);
        return template;
    }

    @Transactional
    public PromptTemplate update(Long id, UpsertPromptTemplateRequest request, CurrentUser user) {
        requireAdmin(user);
        PromptTemplate template = requireById(id);
        apply(template, request);
        template.setUpdatedBy(user.id());
        promptTemplateMapper.updateById(template);
        return requireById(id);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        requireAdmin(user);
        PromptTemplate template = requireById(id);
        template.setDeleted(true);
        template.setUpdatedBy(user.id());
        promptTemplateMapper.updateById(template);
    }

    public RenderedPrompt preview(Long id, Map<String, String> variables, CurrentUser user) {
        requireAdmin(user);
        PromptTemplate template = requireById(id);
        return new RenderedPrompt(template.getName(), template.getVersion(), template.getScene(),
                renderContent(template.getTemplateContent(), variables));
    }

    private void apply(PromptTemplate template, UpsertPromptTemplateRequest request) {
        template.setName(required(request.name(), "name"));
        template.setVersion(required(request.version(), "version"));
        template.setScene(required(request.scene(), "scene"));
        template.setDescription(request.description());
        template.setTemplateContent(required(request.templateContent(), "templateContent"));
        template.setVariables(StringUtils.hasText(request.variables()) ? request.variables() : "[]");
        template.setOutputConstraints(StringUtils.hasText(request.outputConstraints()) ? request.outputConstraints() : "{}");
        template.setEnabled(request.enabled() == null || request.enabled());
    }

    private String renderContent(String templateContent, Map<String, ?> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(templateContent);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = variables == null ? null : variables.get(name);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private PromptTemplate fallback(String scene) {
        PromptTemplate template = new PromptTemplate();
        template.setName("builtin-" + scene.toLowerCase());
        template.setVersion("builtin");
        template.setScene(scene);
        template.setEnabled(true);
        template.setVariables("[]");
        template.setOutputConstraints("{}");
        if ("KNOWLEDGE_QA".equals(scene)) {
            template.setTemplateContent("""
                    你是企业合规知识库助手。只能基于以下知识库片段回答，不要编造规则。

                    问题：
                    {{question}}

                    知识库片段：
                    {{context}}

                    请给出简洁答案，并列出依据。""");
        } else if ("REPORT_GENERATION".equals(scene)) {
            template.setTemplateContent("根据审查结果生成报告摘要：{{review_context}}");
        } else {
            template.setTemplateContent("请处理场景 " + JsonStrings.escape(scene) + "：{{input}}");
        }
        return template;
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.permissions().contains("admin:manage")) {
            throw new IllegalArgumentException("admin:manage permission is required.");
        }
    }

    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    public record UpsertPromptTemplateRequest(String name, String version, String scene, String description,
                                              String templateContent, String variables, String outputConstraints,
                                              Boolean enabled) {
    }

    public record RenderedPrompt(String name, String version, String scene, String content) {
    }
}
