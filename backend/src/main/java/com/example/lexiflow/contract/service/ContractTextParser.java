package com.example.lexiflow.contract.service;

import com.example.lexiflow.contract.model.Contract;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class ContractTextParser {

    private static final Charset GBK = Charset.forName("GBK");

    public ParseResult parse(Contract contract) {
        try {
            if ("txt".equalsIgnoreCase(contract.getFileType())) {
                TextContent text = readPlainText(Path.of(contract.getFilePath()));
                return ParseResult.success(text.text(), "Read " + text.charsetName() + " plain text from uploaded txt file.");
            }
            if ("docx".equalsIgnoreCase(contract.getFileType())) {
                String text = readDocx(Path.of(contract.getFilePath()));
                if (text.isBlank()) {
                    return ParseResult.failure("DOCX file does not contain readable text.");
                }
                return ParseResult.success(text, "Read text from uploaded docx file.");
            }
            return ParseResult.failure("Unsupported contract file type: " + contract.getFileType());
        } catch (IOException ex) {
            return ParseResult.failure("Failed to read contract file: " + ex.getMessage());
        }
    }

    private TextContent readPlainText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return new TextContent(decodeStrict(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8.name());
        } catch (CharacterCodingException ex) {
            return new TextContent(decodeStrict(bytes, GBK), GBK.name());
        }
    }

    private String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
        return decoded.toString();
    }

    private String readDocx(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private record TextContent(String text, String charsetName) {
    }

    public record ParseResult(boolean success, String text, String message) {
        public static ParseResult success(String text, String message) {
            return new ParseResult(true, text, message);
        }

        public static ParseResult failure(String message) {
            return new ParseResult(false, null, message);
        }
    }
}
