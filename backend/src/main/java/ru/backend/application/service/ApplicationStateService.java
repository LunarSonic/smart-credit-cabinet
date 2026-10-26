package ru.backend.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.ApplicationStatusHistory;
import ru.backend.application.entity.enums.ActorType;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.repository.StatusHistoryRepository;
import ru.backend.common.BusinessException;

import java.util.UUID;

/**
 * Единственная точка изменения статуса заявки.
 * Проверяет допустимость перехода (state machine), обновляет сущность
 * и записывает append-only строку в application_status_history.
 */
@Service
public class ApplicationStateService {

    private final ApplicationRepository apps;
    private final StatusHistoryRepository history;

    public ApplicationStateService(ApplicationRepository apps, StatusHistoryRepository history) {
        this.apps    = apps;
        this.history = history;
    }

    /**
     * Выполняет переход {@code a.status → target}.
     * Бросает 409 Conflict, если переход недопустим.
     */
    @Transactional
    public void transition(Application a, ApplicationStatus target,
                           ActorType actorType, UUID actorId,
                           String reason, String comment) {
        if (!a.getStatus().canGoTo(target)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Недопустимый переход статуса: " + a.getStatus() + " → " + target);
        }
        ApplicationStatus from = a.getStatus();
        a.setStatus(target);
        apps.save(a);
        record(a.getId(), from, target, actorType, actorId, reason, comment);
    }

    /** Начальная запись в историю при создании заявки (нет fromStatus). */
    @Transactional
    public void recordCreation(Application a, UUID userId) {
        record(a.getId(), null, a.getStatus(), ActorType.CLIENT, userId, "Заявка создана", null);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void record(UUID applicationId,
                        ApplicationStatus from, ApplicationStatus to,
                        ActorType actorType, UUID actorId,
                        String reason, String comment) {
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplicationId(applicationId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorType(actorType);
        h.setActorId(actorId);
        h.setReason(reason);
        h.setComment(comment);
        history.save(h);
    }
}
