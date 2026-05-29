package com.example.lexiflow.contract.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractClause;
import com.example.lexiflow.contract.service.ClauseExtractionService;
import com.example.lexiflow.contract.service.ContractService;
import com.example.lexiflow.security.CurrentUser;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/contracts")
public class ContractController {

    private final ContractService contractService;
    private final ClauseExtractionService clauseExtractionService;

    public ContractController(ContractService contractService, ClauseExtractionService clauseExtractionService) {
        this.contractService = contractService;
        this.clauseExtractionService = clauseExtractionService;
    }

    @PostMapping
    public ApiResponse<Contract> upload(@RequestParam("file") @NotNull MultipartFile file,
                                        @RequestParam(required = false) String contractName,
                                        @RequestParam(required = false) String contractType,
                                        @RequestParam(required = false) BigDecimal contractAmount,
                                        @RequestParam(required = false) String customerName,
                                        @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(contractService.upload(file, contractName, contractType, contractAmount, customerName, user));
    }

    @GetMapping
    public ApiResponse<List<Contract>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(contractService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Contract> detail(@PathVariable Long id) {
        return ApiResponse.ok(contractService.requireById(id));
    }

    @GetMapping("/{id}/original")
    public ApiResponse<OriginalTextResponse> original(@PathVariable Long id) {
        return ApiResponse.ok(new OriginalTextResponse(contractService.originalText(id)));
    }

    @PostMapping("/{id}/parse")
    public ApiResponse<Contract> parse(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(contractService.parse(id, user));
    }

    @PostMapping("/{id}/clauses/extract")
    public ApiResponse<List<ContractClause>> extractClauses(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        Contract contract = contractService.requireById(id);
        return ApiResponse.ok(clauseExtractionService.extract(contract, user));
    }

    @GetMapping("/{id}/clauses")
    public ApiResponse<List<ContractClause>> clauses(@PathVariable Long id) {
        contractService.requireById(id);
        return ApiResponse.ok(clauseExtractionService.listByContract(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Contract> archive(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(contractService.archive(id, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        contractService.delete(id, user);
        return ApiResponse.ok(null);
    }

    public record OriginalTextResponse(String text) {
    }
}
