package com.example.lexiflow;

import org.junit.jupiter.api.Test;

class LexiFlowApplicationTests {

    @Test
    void applicationClassIsAvailable() {
        LexiFlowApplication application = new LexiFlowApplication();
        org.assertj.core.api.Assertions.assertThat(application).isNotNull();
    }
}

