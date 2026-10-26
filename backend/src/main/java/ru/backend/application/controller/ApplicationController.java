package ru.backend.application.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.backend.application.dto.ApplicationDetailDto;
import ru.backend.application.dto.ApplicationSummaryDto;
import ru.backend.application.dto.CreateApplicationRequest;
import ru.backend.application.dto.HistoryDto;
import ru.backend.application.dto.SubmitRequest;
import ru.backend.application.dto.UpdateApplicationRequest;
import ru.backend.application.service.ApplicationService;
import ru.backend.session.AuthUser;

import java.util.List;
import java.util.UUID;

/**
 * Этапы 2 и 7 для клиента: создание, редактирование, отправка, отмена заявки
 * Статус обновляется опросом (polling) GET /api/applications/{id}
 */

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDetailDto create(@AuthenticationPrincipal AuthUser u,
                                       @Valid @RequestBody CreateApplicationRequest r) {
        return service.create(u, r);
    }

    @GetMapping
    public List<ApplicationSummaryDto> list(@AuthenticationPrincipal AuthUser u) {
        return service.list(u);
    }

    @GetMapping("/{id}")
    public ApplicationDetailDto get(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) {
        return service.get(u, id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryDto> history(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) {
        return service.get(u, id).history();
    }

    @PutMapping("/{id}")
    public ApplicationDetailDto update(@AuthenticationPrincipal AuthUser u,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody UpdateApplicationRequest r) {
        return service.update(u, id, r);
    }

    @PostMapping("/{id}/submit")
    public ApplicationDetailDto submit(@AuthenticationPrincipal AuthUser u,
                                       @PathVariable UUID id,
                                       @RequestBody SubmitRequest r) {
        return service.submit(u, id, r);
    }

    @PostMapping("/{id}/cancel")
    public ApplicationDetailDto cancel(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) {
        return service.cancel(u, id);
    }
}
