package ru.backend.application.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.backend.application.service.PipelineService;

@Component
public class PipelineTrigger {
    private final PipelineService pipeline;

    public PipelineTrigger(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @Async("pipelineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ApplicationSubmittedEvent e) {
        pipeline.advance(e.applicationId());
    }
}
