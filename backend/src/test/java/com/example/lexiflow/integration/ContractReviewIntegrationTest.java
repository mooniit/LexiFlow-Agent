package com.example.lexiflow.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.contract.mapper.ContractMapper;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.review.mapper.ClauseRiskMapper;
import com.example.lexiflow.review.mapper.ContractReviewMapper;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.review.model.ContractReview;
import com.example.lexiflow.review.service.ContractReviewService;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ContractReviewIntegrationTest extends TestcontainersBaseTest {

    @Autowired
    private ContractReviewService reviewService;
    @Autowired
    private ContractReviewMapper reviewMapper;
    @Autowired
    private ContractMapper contractMapper;
    @Autowired
    private ClauseRiskMapper riskMapper;

    @Test
    void createsReviewAndPersistsToDatabase() {
        Contract contract = createTestContract();

        ContractReview review = reviewService.create(contract.getId(), testUser());

        Assertions.assertThat(review.getId()).isNotNull();
        Assertions.assertThat(review.getStatus()).isEqualTo("CREATED");
        Assertions.assertThat(review.getProgressPercent()).isEqualTo(0);

        ContractReview persisted = reviewMapper.selectById(review.getId());
        Assertions.assertThat(persisted).isNotNull();
        Assertions.assertThat(persisted.getContractId()).isEqualTo(contract.getId());
    }

    @Test
    void listsReviewsByContractId() {
        Contract contract = createTestContract();
        reviewService.create(contract.getId(), testUser());
        reviewService.create(contract.getId(), testUser());

        List<ContractReview> reviews = reviewService.list(contract.getId());

        Assertions.assertThat(reviews).hasSize(2);
    }

    @Test
    void cancelsReviewSuccessfully() {
        Contract contract = createTestContract();
        ContractReview review = reviewService.create(contract.getId(), testUser());

        ContractReview cancelled = reviewService.cancel(review.getId(), testUser());

        Assertions.assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void requiresExistingReviewThrowsWhenNotFound() {
        Assertions.assertThatThrownBy(() -> reviewService.requireById(99999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Review not found");
    }

    @Test
    void onlyFailedReviewCanBeRerun() {
        Contract contract = createTestContract();
        ContractReview review = reviewService.create(contract.getId(), testUser());

        Assertions.assertThatThrownBy(() -> reviewService.rerun(review.getId(), testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only failed review tasks can be rerun");
    }

    @Test
    void reviewStepsAreAvailableAfterCancellation() {
        Contract contract = createTestContract();
        ContractReview review = reviewService.create(contract.getId(), testUser());
        reviewService.cancel(review.getId(), testUser());

        var steps = reviewService.steps(review.getId());

        Assertions.assertThat(steps).isEmpty();
    }

    private Contract createTestContract() {
        Contract contract = new Contract();
        contract.setContractName("集成测试合同");
        contract.setContractType("SALES");
        contract.setFileType("txt");
        contract.setFilePath("test/path.txt");
        contract.setOriginalFilename("test.txt");
        contract.setStatus("UPLOADED");
        contract.setMetadata("{}");
        contractMapper.insert(contract);
        return contract;
    }

    private CurrentUser testUser() {
        return new CurrentUser(1L, "tester", "Test User", null, List.of("ADMIN"),
                List.of("contract:read", "contract:write", "review:read", "review:write"), true);
    }
}
