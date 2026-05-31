package com.example.lexiflow.llm.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TokenUsageTest {

    @Test
    void totalTokensIsSumOfPromptAndCompletion() {
        TokenUsage usage = new TokenUsage(150, 80);
        Assertions.assertThat(usage.totalTokens()).isEqualTo(230);
    }

    @Test
    void zeroTokensForEmptyInteraction() {
        TokenUsage usage = new TokenUsage(0, 0);
        Assertions.assertThat(usage.totalTokens()).isZero();
    }

    @Test
    void promptOnlyInteractionTotalsCorrectly() {
        TokenUsage usage = new TokenUsage(42, 0);
        Assertions.assertThat(usage.promptTokens()).isEqualTo(42);
        Assertions.assertThat(usage.completionTokens()).isZero();
        Assertions.assertThat(usage.totalTokens()).isEqualTo(42);
    }

    @Test
    void tokenUsageRecordIsImmutable() {
        TokenUsage usage = new TokenUsage(100, 50);

        Assertions.assertThat(usage.promptTokens()).isEqualTo(100);
        Assertions.assertThat(usage.completionTokens()).isEqualTo(50);
    }
}
