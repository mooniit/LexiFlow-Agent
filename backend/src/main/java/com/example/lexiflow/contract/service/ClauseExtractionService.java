package com.example.lexiflow.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.contract.mapper.ContractClauseMapper;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractClause;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClauseExtractionService {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:金额|价款|总价|合同金额|amount)[^\\d]{0,12}([0-9]+(?:\\.[0-9]+)?)(?:\\s*)(万元|元|rmb|cny|usd|美元)?");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(?i)([0-9]{1,3})\\s*(?:日|天|days?)");
    private static final Pattern MONTHS_PATTERN = Pattern.compile("(?i)([0-9]{1,3})\\s*(?:个月|月|months?)");

    private final ContractClauseMapper clauseMapper;

    public ClauseExtractionService(ContractClauseMapper clauseMapper) {
        this.clauseMapper = clauseMapper;
    }

    @Transactional
    public List<ContractClause> extract(Contract contract, Long userId) {
        clauseMapper.delete(new LambdaQueryWrapper<ContractClause>().eq(ContractClause::getContractId, contract.getId()));
        String text = contract.getParsedText() == null ? "" : contract.getParsedText();
        List<ContractClause> clauses = new ArrayList<>();

        addIfPresent(clauses, contract, "PARTIES", "合同主体", text, List.of("甲方", "乙方", "party", "buyer", "seller"), Map.of(), userId);
        addAmountClause(clauses, contract, text, userId);
        addTimedClause(clauses, contract, "PAYMENT_TERM", "付款周期", text, List.of("付款", "支付", "payment"), "paymentDays", userId);
        addTimedClause(clauses, contract, "TERM", "合同期限", text, List.of("期限", "有效期", "term"), "termMonths", userId);
        addIfPresent(clauses, contract, "LIABILITY", "违约责任/赔偿责任", text, List.of("违约", "赔偿", "责任", "liability", "indemnify"), Map.of(), userId);
        addIfPresent(clauses, contract, "CONFIDENTIALITY", "保密", text, List.of("保密", "confidential"), Map.of(), userId);
        addIfPresent(clauses, contract, "INTELLECTUAL_PROPERTY", "知识产权", text, List.of("知识产权", "著作权", "专利", "intellectual property", "ip"), Map.of(), userId);
        addIfPresent(clauses, contract, "DATA_PROTECTION", "数据保护", text, List.of("个人信息", "数据保护", "隐私", "personal data", "privacy"), Map.of(), userId);
        addIfPresent(clauses, contract, "DISPUTE_RESOLUTION", "争议解决", text, List.of("争议", "仲裁", "诉讼", "法院", "dispute", "arbitration"), Map.of(), userId);
        addIfPresent(clauses, contract, "TERMINATION", "解除/终止", text, List.of("解除", "终止", "termination"), Map.of(), userId);
        addIfPresent(clauses, contract, "LIABILITY_CAP", "赔偿上限", text, List.of("赔偿上限", "责任上限", "liability cap", "cap"), Map.of(), userId);
        addIfPresent(clauses, contract, "AUTO_RENEWAL", "自动续约", text, List.of("自动续约", "自动延长", "auto renew", "automatic renewal"), Map.of(), userId);

        for (ContractClause clause : clauses) {
            clauseMapper.insert(clause);
        }
        return clauses;
    }

    public List<ContractClause> listByContract(Long contractId) {
        return clauseMapper.selectList(new LambdaQueryWrapper<ContractClause>()
                .eq(ContractClause::getContractId, contractId)
                .eq(ContractClause::getDeleted, false)
                .orderByAsc(ContractClause::getId));
    }

    private void addAmountClause(List<ContractClause> clauses, Contract contract, String text, Long userId) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        Map<String, Object> data = new LinkedHashMap<>();
        String content = findSnippet(text, List.of("金额", "价款", "总价", "amount"));
        if (matcher.find()) {
            data.put("amount", matcher.group(1));
            data.put("unit", matcher.group(2) == null ? "" : matcher.group(2));
            content = snippetAround(text, matcher.start(), matcher.end());
        } else if (contract.getContractAmount() != null) {
            data.put("amount", contract.getContractAmount().toPlainString());
            data.put("source", "contract.metadata");
            content = "合同元数据金额: " + contract.getContractAmount().toPlainString();
        }
        if (!data.isEmpty()) {
            clauses.add(build(contract, "AMOUNT", "合同金额", content, toJson(data), userId));
        }
    }

    private void addTimedClause(List<ContractClause> clauses, Contract contract, String type, String title, String text,
                                List<String> keywords, String dataKey, Long userId) {
        String snippet = findSnippet(text, keywords);
        if (snippet.isBlank()) {
            return;
        }
        Matcher days = DAYS_PATTERN.matcher(snippet);
        Matcher months = MONTHS_PATTERN.matcher(snippet);
        Map<String, Object> data = new LinkedHashMap<>();
        if (days.find()) {
            data.put(dataKey, days.group(1));
        } else if (months.find()) {
            data.put(dataKey, String.valueOf(Integer.parseInt(months.group(1)) * 30));
        }
        clauses.add(build(contract, type, title, snippet, toJson(data), userId));
    }

    private void addIfPresent(List<ContractClause> clauses, Contract contract, String type, String title, String text,
                              List<String> keywords, Map<String, Object> data, Long userId) {
        String snippet = findSnippet(text, keywords);
        if (!snippet.isBlank()) {
            clauses.add(build(contract, type, title, snippet, toJson(data), userId));
        }
    }

    private ContractClause build(Contract contract, String type, String title, String content, String data, Long userId) {
        ContractClause clause = new ContractClause();
        clause.setContractId(contract.getId());
        clause.setClauseType(type);
        clause.setClauseName(title);
        clause.setClauseText(content);
        clause.setStructuredData(data);
        clause.setSequenceOrder(0);
        clause.setCreatedBy(userId);
        clause.setUpdatedBy(userId);
        return clause;
    }

    private String findSnippet(String text, List<String> keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            int index = lower.indexOf(keyword.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                return snippetAround(text, index, index + keyword.length());
            }
        }
        return "";
    }

    private String snippetAround(String text, int start, int end) {
        int left = Math.max(0, start - 120);
        int right = Math.min(text.length(), end + 220);
        return text.substring(left, right).trim();
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        List<String> pairs = new ArrayList<>();
        data.forEach((key, value) -> pairs.add(JsonStrings.quote(key) + ":" + JsonStrings.quote(String.valueOf(value))));
        return "{" + String.join(",", pairs) + "}";
    }
}
