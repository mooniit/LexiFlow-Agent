package com.example.lexiflow.rag.service;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentNormalizerTest {

    private final KnowledgeDocumentNormalizer normalizer = new KnowledgeDocumentNormalizer();

    @Test
    void normalizesMarkdownAndRemovesIntroDirectory() {
        String markdown = """
                ---
                title: 示例规则
                ---
                # 法案说明

                本说明不应参与检索。

                目录
                第一条 适用范围 1
                第二条 付款周期 ........ 2

                ## 第一条 适用范围
                本规则适用于销售合同。   

                **第二条 付款周期**
                付款周期不得超过60日。
                """;

        String normalized = normalizer.normalize(markdown, "md");

        Assertions.assertThat(normalized)
                .startsWith("第一条 适用范围")
                .contains("本规则适用于销售合同。")
                .contains("第二条 付款周期")
                .doesNotContain("法案说明")
                .doesNotContain("目录")
                .doesNotContain("........")
                .doesNotContain("**");
    }

    @Test
    void keepsDocxStylePlainTextInSameCanonicalShape() {
        String docxText = """
                编制说明
                这些背景材料不应进入切片。

                第一条 适用范围
                   本规则适用于采购合同。\s\s\s


                第二条 数据保护
                合同应包含数据保护条款。
                """;

        String normalized = normalizer.normalize(docxText, "docx");

        Assertions.assertThat(normalized)
                .isEqualTo("""
                        第一条 适用范围
                        本规则适用于采购合同。

                        第二条 数据保护
                        合同应包含数据保护条款。""");
    }

    @Test
    void splitsByArticleInsteadOfPackingManyArticlesTogether() {
        KnowledgeBaseService service = new KnowledgeBaseService(null, null, null, null, null, null, null, normalizer);
        String content = normalizer.normalize("""
                目录
                第一条 适用范围 ........ 1

                第一条 适用范围
                本规则适用于销售合同。
                第二条 付款周期
                付款周期不得超过60日。
                第三条 数据保护
                合同应包含数据保护条款。
                """, "md");

        List<KnowledgeBaseService.ArticleChunk> chunks = service.splitByArticlesWithMeta(content, 450);

        Assertions.assertThat(chunks).hasSize(3);
        Assertions.assertThat(chunks.get(0).content()).contains("第一条").doesNotContain("第二条");
        Assertions.assertThat(chunks.get(1).content()).contains("第二条").doesNotContain("第三条");
        Assertions.assertThat(chunks.get(2).content()).contains("第三条");
    }
}
