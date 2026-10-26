package ru.backend.bank.controller;

import ru.backend.application.dto.ApplicationSummaryDto;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.bank.dto.BankApplicationDetailDto;
import ru.backend.bank.dto.ManualDecisionRequest;
import ru.backend.bank.service.BankService;
import ru.backend.session.AuthUser;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Рабочее место банка (роль EMPLOYEE): очередь, карточка, решение */

@RestController
@RequestMapping("/api/bank/applications")
public class BankController {
    private final BankService bank;

    public BankController(BankService bank) { this.bank = bank; }

    @GetMapping
    public PagedModel<ApplicationSummaryDto> queue(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bank.list(status, pageable);
    }

    @GetMapping("/{id}")
    public BankApplicationDetailDto get(@PathVariable UUID id) { return bank.get(id); }

    @PostMapping("/{id}/decision")
    public BankApplicationDetailDto decide(@AuthenticationPrincipal AuthUser employee, @PathVariable UUID id,
                                           @Valid @RequestBody ManualDecisionRequest r) {
        return bank.decide(employee, id, r);
    }
}
