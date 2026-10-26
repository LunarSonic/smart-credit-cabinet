package ru.backend.offer.event;

import ru.backend.common.PipelineProperties;
import ru.backend.offer.service.DisbursementService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DisbursementListener {
    private static final Logger log = LoggerFactory.getLogger(DisbursementListener.class);
    private final DisbursementService disbursement;
    private final PipelineProperties props;

    public DisbursementListener(DisbursementService disbursement, PipelineProperties props) {
        this.disbursement = disbursement; this.props = props;
    }

    @Async("pipelineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ContractSignedEvent e) {
        try {
            Thread.sleep(props.disbursementDelayMs());
            disbursement.disburse(e.contractId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("Не удалось смоделировать выдачу по договору {}: будет повторено фоновой задачей", e.contractId(), ex);
        }
    }
}
