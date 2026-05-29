package com.example.lexiflow.contract.service;

import com.example.lexiflow.contract.model.Contract;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ContractTextParserTest {

    private final ContractTextParser parser = new ContractTextParser();

    @TempDir
    Path tempDir;

    @Test
    void parsesUtf8Txt() throws Exception {
        Path file = tempDir.resolve("contract.txt");
        Files.writeString(file, "合同原文 UTF-8", StandardCharsets.UTF_8);

        ContractTextParser.ParseResult result = parser.parse(contract(file, "txt"));

        assertThat(result.success()).isTrue();
        assertThat(result.text()).contains("合同原文 UTF-8");
        assertThat(result.message()).contains("UTF-8");
    }

    @Test
    void fallsBackToGbkTxt() throws Exception {
        Path file = tempDir.resolve("contract-gbk.txt");
        Files.writeString(file, "合同原文 GBK", Charset.forName("GBK"));

        ContractTextParser.ParseResult result = parser.parse(contract(file, "txt"));

        assertThat(result.success()).isTrue();
        assertThat(result.text()).contains("合同原文 GBK");
        assertThat(result.message()).contains("GBK");
    }

    @Test
    void parsesDocxText() throws Exception {
        Path file = tempDir.resolve("contract.docx");
        try (XWPFDocument document = new XWPFDocument();
             OutputStream outputStream = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("DOCX 合同原文");
            document.write(outputStream);
        }

        ContractTextParser.ParseResult result = parser.parse(contract(file, "docx"));

        assertThat(result.success()).isTrue();
        assertThat(result.text()).contains("DOCX 合同原文");
    }

    private Contract contract(Path file, String fileType) {
        Contract contract = new Contract();
        contract.setFilePath(file.toString());
        contract.setFileType(fileType);
        return contract;
    }
}
