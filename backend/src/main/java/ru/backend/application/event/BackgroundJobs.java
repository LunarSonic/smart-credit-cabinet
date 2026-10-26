package ru.backend.application.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.service.PipelineService;
import ru.backend.common.PipelineProperties;
import ru.backend.offer.entity.Contract;
import ru.backend.offer.entity.Offer;
import ru.backend.offer.entity.enums.OfferStatus;
import ru.backend.offer.repository.ContractRepository;
import ru.backend.offer.repository.OfferRepository;
import ru.backend.offer.service.DisbursementService;
import ru.backend.offer.service.OfferService;

import java.time.Instant;
import java.util.List;

/**
 * Страховочные фоновые задачи:
 * - подхват «зависших» заявок (SUBMITTED / DATA_CHECK / SCORING),
 * - истечение оферт,
 * - добивание имитации выдачи после рестарта
 */

@Component
public class BackgroundJobs {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);

    private final ApplicationRepository apps;
    private final OfferRepository offers;
    private final ContractRepository contracts;
    private final PipelineService pipeline;
    private final OfferService offerService;
    private final DisbursementService disbursement;
    private final PipelineProperties props;

    public BackgroundJobs(ApplicationRepository apps, OfferRepository offers,
                          ContractRepository contracts, PipelineService pipeline,
                          OfferService offerService, DisbursementService disbursement,
                          PipelineProperties props) {
        this.apps        = apps;
        this.offers      = offers;
        this.contracts   = contracts;
        this.pipeline    = pipeline;
        this.offerService = offerService;
        this.disbursement = disbursement;
        this.props       = props;
    }

    @Scheduled(fixedDelayString = "${pipeline.recovery-interval-ms}")
    public void recoverStuckApplications() {
        Instant threshold = Instant.now().minusSeconds(props.recoveryAfterSeconds());
        apps.findByStatusInAndUpdatedAtBefore(
                List.of(ApplicationStatus.SUBMITTED,
                        ApplicationStatus.DATA_CHECK,
                        ApplicationStatus.SCORING), threshold)
                .forEach(a -> {
                    log.info("Подхватываем зависшую заявку {} в статусе {}", a.getId(), a.getStatus());
                    pipeline.advance(a.getId());
                });
    }

    @Scheduled(fixedDelayString = "${pipeline.recovery-interval-ms}")
    public void expireOffers() {
        for (Offer o : offers.findByStatusAndValidUntilBefore(OfferStatus.ACTIVE, Instant.now())) {
            try {
                offerService.expire(o.getId());
            } catch (Exception e) {
                log.warn("Истечение оферты {}: {}", o.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${pipeline.recovery-interval-ms}")
    public void retryDisbursements() {
        Instant signedBefore = Instant.now().minusMillis(props.disbursementDelayMs() * 2 + 5_000);
        for (Contract c : contracts.findByDisbursedAtIsNullAndSignedAtBefore(signedBefore)) {
            try {
                disbursement.disburse(c.getId());
            } catch (Exception e) {
                log.warn("Выдача {}: {}", c.getId(), e.getMessage());
            }
        }
    }
}
