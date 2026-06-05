package com.example.lexiflow.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.contract.mapper.ContractClauseMapper;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
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

    private static final Pattern ARTICLE_HEADING_PATTERN = Pattern.compile("(?m)^\\s*第([一二三四五六七八九十百千万零〇两0-9]+)条[\\s　]*(.*?)\\s*$");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:金额|价款|总价|合同金额|amount)[^\\d]{0,12}([0-9]+(?:\\.[0-9]+)?)(?:\\s*)(万元|元|rmb|cny|usd|美元)?");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(?i)([0-9]{1,3})\\s*(?:日|天|days?)");
    private static final Pattern MONTHS_PATTERN = Pattern.compile("(?i)([0-9]{1,3})\\s*(?:个月|月|months?)");
    private static final Pattern SENTENCE_PUNCTUATION_PATTERN = Pattern.compile("[，,。.;；:：!?！？]");

    private final ContractClauseMapper clauseMapper;
    private final ToolPermissionGuard toolPermissionGuard;

    public ClauseExtractionService(ContractClauseMapper clauseMapper, ToolPermissionGuard toolPermissionGuard) {
        this.clauseMapper = clauseMapper;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    @Transactional
    public List<ContractClause> extract(Contract contract, CurrentUser user) {
        toolPermissionGuard.requireAllowed("clause_extraction", user);
        Long userId = user.id();
        clauseMapper.delete(new LambdaQueryWrapper<ContractClause>().eq(ContractClause::getContractId, contract.getId()));
        String text = contract.getParsedText() == null ? "" : contract.getParsedText();
        List<ContractClause> clauses = new ArrayList<>();

        List<ArticleBlock> articleBlocks = splitArticleBlocks(text);
        if (articleBlocks.isEmpty()) {
            addLegacyKeywordClauses(clauses, contract, text, userId);
        } else {
            addArticleBlockClauses(clauses, contract, articleBlocks, userId);
        }

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

    private void addLegacyKeywordClauses(List<ContractClause> clauses, Contract contract, String text, Long userId) {
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
        addIfPresent(clauses, contract, "ACCEPTANCE", "验收", text, List.of("验收", "交付成果", "验收标准", "acceptance"), Map.of(), userId);
        addIfPresent(clauses, contract, "NOTICE", "通知", text, List.of("通知", "书面通知", "notice"), Map.of(), userId);
    }

    private void addArticleBlockClauses(List<ContractClause> clauses, Contract contract, List<ArticleBlock> blocks, Long userId) {
        for (ArticleBlock block : blocks) {
            String type = classifyClauseType(block);
            Map<String, Object> data = extractStructuredData(type, block.content(), contract);
            ContractClause clause = build(contract, type, block.name(), block.content(), toJson(data), userId);
            clause.setSequenceOrder(block.sequenceOrder());
            clauses.add(clause);
        }
    }

    static List<ArticleBlock> splitArticleBlocks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ArticleHeading> headings = new ArrayList<>();
        Matcher matcher = ARTICLE_HEADING_PATTERN.matcher(text);
        int previousOrdinal = 0;
        while (matcher.find()) {
            String fullLine = matcher.group().trim();
            String ordinalText = matcher.group(1);
            String title = matcher.group(2) == null ? "" : matcher.group(2).trim();
            int ordinal = parseChineseOrdinal(ordinalText);
            if (isLikelyArticleHeading(fullLine, title, ordinal, previousOrdinal)) {
                headings.add(new ArticleHeading(matcher.start(), matcher.end(), ordinal, title));
                previousOrdinal = ordinal;
            }
        }
        if (headings.isEmpty()) {
            return List.of();
        }
        List<ArticleBlock> blocks = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            ArticleHeading current = headings.get(i);
            int nextStart = i + 1 < headings.size() ? headings.get(i + 1).lineStart() : text.length();
            String content = text.substring(current.lineStart(), nextStart).trim();
            blocks.add(new ArticleBlock(
                    current.ordinal(),
                    articleName(current.ordinal(), current.title()),
                    content
            ));
        }
        return blocks;
    }

    private static boolean isLikelyArticleHeading(String fullLine, String title, int ordinal, int previousOrdinal) {
        if (ordinal <= 0 || fullLine.length() > 50 || title.length() > 35) {
            return false;
        }
        if (SENTENCE_PUNCTUATION_PATTERN.matcher(title).find()) {
            return false;
        }
        if (previousOrdinal > 0 && ordinal != previousOrdinal + 1) {
            return false;
        }
        String compactTitle = title.replaceAll("\\s+", "");
        return !compactTitle.startsWith("约定") && !compactTitle.startsWith("规定");
    }

    private static String articleName(int ordinal, String title) {
        String number = ordinal <= 10 ? List.of("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十").get(ordinal)
                : String.valueOf(ordinal);
        return title == null || title.isBlank() ? "第" + number + "条" : "第" + number + "条 " + title.trim();
    }

    private static int parseChineseOrdinal(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String value = raw.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(value);
        }
        value = value.replace('零', '〇').replace('两', '二');
        int hundredIndex = value.indexOf('百');
        if (hundredIndex >= 0) {
            int hundreds = hundredIndex == 0 ? 1 : chineseDigit(value.charAt(hundredIndex - 1));
            int rest = parseChineseOrdinal(value.substring(hundredIndex + 1));
            return hundreds * 100 + Math.max(rest, 0);
        }
        int tenIndex = value.indexOf('十');
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : chineseDigit(value.charAt(tenIndex - 1));
            int ones = tenIndex == value.length() - 1 ? 0 : chineseDigit(value.charAt(tenIndex + 1));
            return tens * 10 + Math.max(ones, 0);
        }
        return chineseDigit(value.charAt(value.length() - 1));
    }

    private static int chineseDigit(char digit) {
        return switch (digit) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            case '十' -> 10;
            default -> -1;
        };
    }

    private String classifyClauseType(ArticleBlock block) {
        String text = block.content().toLowerCase(Locale.ROOT);
        String name = block.name().toLowerCase(Locale.ROOT);
        String combined = name + "\n" + text;
        if (containsAny(combined, "赔偿上限", "责任上限", "liability cap")) {
            return "LIABILITY_CAP";
        }
        if (containsAny(combined, "付款", "支付", "payment")) {
            return "PAYMENT_TERM";
        }
        if (containsAny(combined, "金额", "价款", "总价", "合同金额", "amount")) {
            return "AMOUNT";
        }
        if (containsAny(combined, "期限", "有效期", "term")) {
            return "TERM";
        }
        if (containsAny(combined, "违约", "赔偿", "责任", "liability", "indemnify")) {
            return "LIABILITY";
        }
        if (containsAny(combined, "保密", "confidential")) {
            return "CONFIDENTIALITY";
        }
        if (containsAny(combined, "知识产权", "著作权", "专利", "intellectual property", "ip")) {
            return "INTELLECTUAL_PROPERTY";
        }
        if (containsAny(combined, "个人信息", "数据保护", "隐私", "personal data", "privacy")) {
            return "DATA_PROTECTION";
        }
        if (containsAny(combined, "争议", "仲裁", "诉讼", "法院", "dispute", "arbitration")) {
            return "DISPUTE_RESOLUTION";
        }
        if (containsAny(combined, "解除", "终止", "termination")) {
            return "TERMINATION";
        }
        if (containsAny(combined, "自动续约", "自动延长", "auto renew", "automatic renewal")) {
            return "AUTO_RENEWAL";
        }
        if (containsAny(combined, "验收", "交付成果", "验收标准", "acceptance")) {
            return "ACCEPTANCE";
        }
        if (containsAny(combined, "通知", "书面通知", "notice")) {
            return "NOTICE";
        }
        if (containsAny(name, "合同主体", "主体", "当事人", "甲方", "乙方", "party", "buyer", "seller")) {
            return "PARTIES";
        }
        return "OTHER";
    }

    private Map<String, Object> extractStructuredData(String type, String text, Contract contract) {
        Map<String, Object> data = new LinkedHashMap<>();
        if ("AMOUNT".equals(type)) {
            Matcher matcher = AMOUNT_PATTERN.matcher(text);
            if (matcher.find()) {
                data.put("amount", matcher.group(1));
                data.put("unit", matcher.group(2) == null ? "" : matcher.group(2));
            } else if (contract.getContractAmount() != null) {
                data.put("amount", contract.getContractAmount().toPlainString());
                data.put("source", "contract.metadata");
            }
        }
        if ("PAYMENT_TERM".equals(type) || "TERM".equals(type)) {
            Matcher days = DAYS_PATTERN.matcher(text);
            Matcher months = MONTHS_PATTERN.matcher(text);
            String key = "PAYMENT_TERM".equals(type) ? "paymentDays" : "termMonths";
            if (days.find()) {
                data.put(key, days.group(1));
            } else if (months.find()) {
                data.put(key, "PAYMENT_TERM".equals(type)
                        ? String.valueOf(Integer.parseInt(months.group(1)) * 30)
                        : months.group(1));
            }
        }
        return data;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    record ArticleBlock(int sequenceOrder, String name, String content) {
    }

    private record ArticleHeading(int lineStart, int lineEnd, int ordinal, String title) {
    }
}
