package com.example.lexiflow.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.config.StorageProperties;
import com.example.lexiflow.contract.mapper.ContractMapper;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.contract.model.ContractStatus;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContractService {

    private final ContractMapper contractMapper;
    private final StorageProperties storageProperties;
    private final ContractTextParser textParser;
    private final ToolPermissionGuard toolPermissionGuard;

    public ContractService(ContractMapper contractMapper, StorageProperties storageProperties, ContractTextParser textParser,
                           ToolPermissionGuard toolPermissionGuard) {
        this.contractMapper = contractMapper;
        this.storageProperties = storageProperties;
        this.textParser = textParser;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    @Transactional
    public Contract upload(MultipartFile file, String contractName, String contractType, BigDecimal contractAmount,
                           String customerName, CurrentUser user) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "contract.txt" : file.getOriginalFilename());
        String fileType = resolveFileType(originalFilename);
        if (!List.of("txt", "docx").contains(fileType)) {
            throw new IllegalArgumentException("Only txt and docx contracts are supported.");
        }

        try {
            Path targetDir = Path.of(storageProperties.contractsDir()).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(UUID.randomUUID() + "." + fileType).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Contract contract = new Contract();
            contract.setContractName(StringUtils.hasText(contractName) ? contractName : originalFilename);
            contract.setContractType(contractType);
            contract.setUploaderId(user.id());
            contract.setContractAmount(contractAmount);
            contract.setCustomerName(customerName);
            contract.setStatus(ContractStatus.UPLOADED.name());
            contract.setFileType(fileType);
            contract.setFilePath(target.toString());
            contract.setOriginalFilename(originalFilename);
            contract.setMetadata("{}");
            contract.setCreatedBy(user.id());
            contract.setUpdatedBy(user.id());
            contractMapper.insert(contract);
            return contract;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store contract file.", ex);
        }
    }

    public List<Contract> list(String status) {
        LambdaQueryWrapper<Contract> query = new LambdaQueryWrapper<Contract>()
                .eq(Contract::getDeleted, false)
                .orderByDesc(Contract::getCreatedAt);
        if (StringUtils.hasText(status)) {
            query.eq(Contract::getStatus, status);
        }
        return contractMapper.selectList(query);
    }

    public Contract requireById(Long id) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null || Boolean.TRUE.equals(contract.getDeleted())) {
            throw new IllegalArgumentException("Contract not found: " + id);
        }
        return contract;
    }

    @Transactional
    public Contract parse(Long id, CurrentUser user) {
        toolPermissionGuard.requireAllowed("contract_parse", user);
        Contract contract = requireById(id);
        ContractTextParser.ParseResult result = textParser.parse(contract);
        if (result.success()) {
            contract.setParsedText(result.text());
            contract.setStatus(ContractStatus.PARSED.name());
        } else {
            contract.setStatus(ContractStatus.PARSE_FAILED.name());
            contract.setMetadata("{\"parse_failure\":" + JsonStrings.quote(result.message()) + "}");
        }
        contract.setUpdatedBy(user.id());
        contractMapper.updateById(contract);
        return contract;
    }

    @Transactional
    public Contract archive(Long id, CurrentUser user) {
        Contract contract = requireById(id);
        contract.setStatus(ContractStatus.ARCHIVED.name());
        contract.setUpdatedBy(user.id());
        contractMapper.updateById(contract);
        return contract;
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Contract contract = requireById(id);
        contract.setDeleted(true);
        contract.setUpdatedBy(user.id());
        contract.setUpdatedAt(OffsetDateTime.now());
        contractMapper.updateById(contract);
    }

    public String originalText(Long id) {
        Contract contract = requireById(id);
        return textParser.parse(contract).text();
    }

    private String resolveFileType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

}
