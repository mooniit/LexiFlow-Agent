package com.example.lexiflow;

import com.example.lexiflow.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
public class LexiFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LexiFlowApplication.class, args);
    }
}
