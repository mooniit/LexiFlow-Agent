package com.example.lexiflow.contract.service;

import java.math.BigDecimal;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ContractServiceTest {

    private static final String CONTRACT_TEXT = """
            2025年度销售合同

            甲方（卖方）：深圳市星辰科技有限公司
            乙方（买方）：北京宏远商贸有限公司

            第二条 合同金额
            2.1 本合同总金额为人民币（大写）：壹佰贰拾伍万陆仟元整（￥1,256,000.00元）。
            """;

    @Test
    void extractsComparableFactsFromParsedContractText() {
        ContractService.ExtractedContractFacts facts = ContractService.extractFacts(CONTRACT_TEXT);

        Assertions.assertThat(facts.customerName()).isEqualTo("北京宏远商贸有限公司");
        Assertions.assertThat(facts.contractAmount()).isEqualByComparingTo(new BigDecimal("1256000.00"));
    }

    @Test
    void rejectsSubmittedCustomerWhenParsedBuyerDiffers() {
        ContractService.ExtractedContractFacts facts = ContractService.extractFacts(CONTRACT_TEXT);

        Assertions.assertThatThrownBy(() ->
                        ContractService.validateSubmittedMetadata(new BigDecimal("1256000.00"), "aaaaaaaaaaaaa", facts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerName does not match contract text");
    }

    @Test
    void rejectsSubmittedAmountWhenParsedAmountDiffers() {
        ContractService.ExtractedContractFacts facts = ContractService.extractFacts(CONTRACT_TEXT);

        Assertions.assertThatThrownBy(() ->
                        ContractService.validateSubmittedMetadata(new BigDecimal("20000000000.00"), "北京宏远商贸有限公司", facts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contractAmount does not match contract text");
    }
}
