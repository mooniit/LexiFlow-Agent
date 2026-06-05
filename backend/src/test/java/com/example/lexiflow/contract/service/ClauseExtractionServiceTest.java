package com.example.lexiflow.contract.service;

import com.example.lexiflow.contract.mapper.ContractClauseMapper;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ClauseExtractionServiceTest {

    @Test
    void extractsArticleBlocksWithoutSplittingInlineArticleReferences() {
        Contract contract = new Contract();
        contract.setId(10L);
        contract.setParsedText("""
                2025年度采购合同
                一、合同说明
                本段属于合同首部说明，不应作为条款块。

                第一条 合同标的
                甲方向乙方销售服务器及配套服务。

                第二条 付款方式
                乙方应在验收后90日内支付剩余款项。
                第三条约定的验收标准仍适用于付款条件。

                第三条 验收
                乙方应在交付后5日内完成验收。

                第四条 违约责任
                任何一方违约，应承担赔偿责任。
                """);

        ContractClauseMapper clauseMapper = Mockito.mock(ContractClauseMapper.class);
        ToolPermissionGuard guard = Mockito.mock(ToolPermissionGuard.class);
        ClauseExtractionService service = new ClauseExtractionService(clauseMapper, guard);

        List<ContractClause> clauses = service.extract(contract, user());

        Assertions.assertThat(clauses)
                .extracting(ContractClause::getClauseName)
                .containsExactly("第一条 合同标的", "第二条 付款方式", "第三条 验收", "第四条 违约责任");
        Assertions.assertThat(clauses)
                .filteredOn(clause -> clause.getClauseName().equals("第二条 付款方式"))
                .singleElement()
                .satisfies(clause -> Assertions.assertThat(clause.getClauseText())
                        .contains("第三条约定的验收标准仍适用于付款条件")
                        .doesNotContain("第三条 验收"));
        Assertions.assertThat(clauses)
                .extracting(ContractClause::getClauseType)
                .containsExactly("OTHER", "PAYMENT_TERM", "ACCEPTANCE", "LIABILITY");

        ArgumentCaptor<ContractClause> captor = ArgumentCaptor.forClass(ContractClause.class);
        Mockito.verify(clauseMapper, Mockito.times(4)).insert(captor.capture());
        Assertions.assertThat(captor.getAllValues())
                .extracting(ContractClause::getSequenceOrder)
                .containsExactly(1, 2, 3, 4);
    }

    private CurrentUser user() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("tool:execute"), true);
    }
}
