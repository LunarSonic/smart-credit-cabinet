package ru.backend.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.decision.client.MlDecideRequest;
import ru.backend.decision.client.MlDecideResponse;
import ru.backend.decision.client.MlDecisionClient;
import ru.backend.decision.client.MlUnavailableException;

import java.util.Optional;
import java.util.UUID;

@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final PipelineSteps steps;
    private final MlDecisionClient ml;
    private final ApplicationRepository apps;

    public PipelineService(PipelineSteps steps, MlDecisionClient ml, ApplicationRepository apps) {
        this.steps = steps;
        this.ml    = ml;
        this.apps  = apps;
    }

    public void advance(UUID applicationId) {
        for (int i = 0; i < 8; i++) {
            try {
                Optional<Application> opt = apps.findById(applicationId);
                if (opt.isEmpty()) return;
                ApplicationStatus s = opt.get().getStatus();
                switch (s) {
                    case SUBMITTED   -> steps.completeness(applicationId);
                    case DATA_CHECK  -> steps.dataCheck(applicationId);
                    case SCORING     -> score(applicationId);
                    default          -> {
                        return;
                    }
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                log.info("Заявка {} изменена параллельно, конвейер остановлен", applicationId);
                return;
            } catch (Exception e) {
                log.error("Сбой конвейера для заявки {}; подхватит фоновая задача", applicationId, e);
                return;
            }
        }
    }

    private void score(UUID id) {
        MlDecideRequest req = steps.prepareScoring(id);
        if (req == null) return;
        try {
            MlDecideResponse resp = ml.decide(req);
            steps.applyDecision(id, req.requestId(), resp);
        } catch (MlUnavailableException e) {
            steps.scoringFailed(id, e.getMessage());
        }
    }
}
