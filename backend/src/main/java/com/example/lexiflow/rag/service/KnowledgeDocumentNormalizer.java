package com.example.lexiflow.rag.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentNormalizer {

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A\\s*---\\s*\\n[\\s\\S]*?\\n---\\s*\\n?");
    private static final Pattern ARTICLE_START = Pattern.compile("(?m)^\\s*(第\\s*[一二三四五六七八九十百千万零〇两0-9]+\\s*条|Article\\s+\\d+)(?:\\s|$|[：:、.．]).*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s*");
    private static final Pattern MARKDOWN_LIST_MARKER = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)、]\\s+)");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern TOC_LINE = Pattern.compile("^\\s*(?:第.+?[章节条]|\\d+(?:\\.\\d+)*)\\s+.+?\\.{2,}\\s*\\d+\\s*$");
    private static final Pattern PAGE_NUMBER = Pattern.compile("^\\s*-?\\s*\\d+\\s*-?\\s*$");

    public String normalize(String rawText, String fileType) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String text = rawText.replace("\uFEFF", "")
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        if ("md".equalsIgnoreCase(fileType)) {
            text = normalizeMarkdown(text);
        }
        text = removeIntroAndDirectory(text);
        return normalizeLines(text);
    }

    private String normalizeMarkdown(String text) {
        String normalized = FRONT_MATTER.matcher(text).replaceFirst("");
        normalized = normalized.replaceAll("(?m)^```.*$", "");
        normalized = MARKDOWN_LINK.matcher(normalized).replaceAll("$1");
        normalized = normalized.replaceAll("(?m)^\\s*#{1,6}\\s*", "");
        normalized = normalized.replace("**", "")
                .replace("__", "")
                .replace("`", "");
        return normalized;
    }

    private String removeIntroAndDirectory(String text) {
        String withoutDirectory = removeExplicitDirectoryBlock(text);
        Matcher matcher = ARTICLE_START.matcher(withoutDirectory);
        if (matcher.find()) {
            return withoutDirectory.substring(matcher.start());
        }
        return withoutDirectory;
    }

    private String removeExplicitDirectoryBlock(String text) {
        String[] lines = text.split("\n");
        List<String> kept = new ArrayList<>();
        boolean inDirectory = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("目录") || trimmed.equalsIgnoreCase("contents")) {
                inDirectory = true;
                continue;
            }
            if (inDirectory) {
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (looksLikeDirectoryEntry(trimmed)) {
                    continue;
                }
                inDirectory = false;
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    private boolean looksLikeDirectoryEntry(String line) {
        return TOC_LINE.matcher(line).matches()
                || line.matches("^\\s*(?:第.+?[章节条]|\\d+(?:\\.\\d+)*)\\s+.+?\\s+\\d+\\s*$");
    }

    private String normalizeLines(String text) {
        String[] lines = text.split("\n");
        List<String> normalized = new ArrayList<>();
        boolean previousBlank = false;
        for (String rawLine : lines) {
            String line = cleanLine(rawLine);
            if (line.isEmpty() || shouldDropLine(line)) {
                if (!previousBlank && !normalized.isEmpty()) {
                    normalized.add("");
                    previousBlank = true;
                }
                continue;
            }
            normalized.add(line);
            previousBlank = false;
        }
        while (!normalized.isEmpty() && normalized.get(normalized.size() - 1).isEmpty()) {
            normalized.remove(normalized.size() - 1);
        }
        return String.join("\n", normalized).trim();
    }

    private String cleanLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        line = MARKDOWN_HEADING.matcher(line).replaceFirst("");
        line = MARKDOWN_LIST_MARKER.matcher(line).replaceFirst("");
        if (line.startsWith("|") && line.endsWith("|")) {
            line = line.substring(1, line.length() - 1).replace('|', ' ');
        }
        return line.replaceAll("[\\t ]+", " ").trim();
    }

    private boolean shouldDropLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return TABLE_SEPARATOR.matcher(line).matches()
                || TOC_LINE.matcher(line).matches()
                || PAGE_NUMBER.matcher(line).matches()
                || lower.startsWith("copyright ")
                || lower.startsWith("版权所有");
    }
}
