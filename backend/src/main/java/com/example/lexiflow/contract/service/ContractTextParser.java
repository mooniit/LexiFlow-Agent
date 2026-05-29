package com.example.lexiflow.contract.service;

import com.example.lexiflow.contract.model.Contract;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class ContractTextParser {

    public ParseResult parse(Contract contract) {
        try {
            if ("txt".equalsIgnoreCase(contract.getFileType())) {
                String text = Files.readString(Path.of(contract.getFilePath()), StandardCharsets.UTF_8);
                return ParseResult.success(text, "Read UTF-8 plain text from uploaded txt file.");
            }
            if ("docx".equalsIgnoreCase(contract.getFileType())) {
                return ParseResult.failure("DOCX parsing requires Apache POI and is reserved for the next parser iteration.");
            }
            return ParseResult.failure("Unsupported contract file type: " + contract.getFileType());
        } catch (IOException ex) {
            return ParseResult.failure("Failed to read contract file: " + ex.getMessage());
        }
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
