package ru.backend;

import org.junit.jupiter.api.Test;
import ru.backend.application.entity.enums.ApplicationStatus;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты state machine ApplicationStatus.
 * Проверяют допустимые переходы, конечные состояния и клиентский маппинг
 */

class ApplicationStateMachineTest {

    // ─── Допустимые переходы ──────────────────────────────────────────────────

    @Test
    void draft_canGoTo_submitted_and_cancelled() {
        assertThat(ApplicationStatus.DRAFT.canGoTo(ApplicationStatus.SUBMITTED)).isTrue();
        assertThat(ApplicationStatus.DRAFT.canGoTo(ApplicationStatus.CANCELLED)).isTrue();
    }

    @Test
    void submitted_canGoTo_completenessCheck_and_cancelled() {
        assertThat(ApplicationStatus.SUBMITTED.canGoTo(ApplicationStatus.COMPLETENESS_CHECK)).isTrue();
        assertThat(ApplicationStatus.SUBMITTED.canGoTo(ApplicationStatus.CANCELLED)).isTrue();
    }

    @Test
    void completenessCheck_canGoTo_needsInfo_or_dataCheck() {
        assertThat(ApplicationStatus.COMPLETENESS_CHECK.canGoTo(ApplicationStatus.NEEDS_INFO)).isTrue();
        assertThat(ApplicationStatus.COMPLETENESS_CHECK.canGoTo(ApplicationStatus.DATA_CHECK)).isTrue();
    }

    @Test
    void needsInfo_canGoTo_dataCheck_directly() {
        // Spec: NEEDS_INFO → DATA_CHECK (без повторной проверки полноты)
        assertThat(ApplicationStatus.NEEDS_INFO.canGoTo(ApplicationStatus.DATA_CHECK)).isTrue();
    }

    @Test
    void approved_canGoTo_signed_offerDeclined_offerExpired_cancelled() {
        Set<ApplicationStatus> expected = Set.of(
                ApplicationStatus.SIGNED,
                ApplicationStatus.OFFER_DECLINED,
                ApplicationStatus.OFFER_EXPIRED,
                ApplicationStatus.CANCELLED);
        assertThat(ApplicationStatus.APPROVED.allowedTargets()).isEqualTo(expected);
    }

    // ─── Недопустимые переходы ────────────────────────────────────────────────

    @Test
    void draft_cannotGoTo_approved() {
        assertThat(ApplicationStatus.DRAFT.canGoTo(ApplicationStatus.APPROVED)).isFalse();
    }

    @Test
    void approved_cannotGoTo_rejected() {
        // После одобрения нельзя перейти в REJECTED напрямую
        assertThat(ApplicationStatus.APPROVED.canGoTo(ApplicationStatus.REJECTED)).isFalse();
    }

    @Test
    void signed_cannotBeCancelled() {
        assertThat(ApplicationStatus.SIGNED.canGoTo(ApplicationStatus.CANCELLED)).isFalse();
    }

    @Test
    void disbursed_isTerminal() {
        assertThat(ApplicationStatus.DISBURSED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.DISBURSED.allowedTargets()).isEmpty();
    }

    @Test
    void rejected_isTerminal() {
        assertThat(ApplicationStatus.REJECTED.isTerminal()).isTrue();
    }

    @Test
    void offerDeclined_isTerminal() {
        assertThat(ApplicationStatus.OFFER_DECLINED.isTerminal()).isTrue();
    }

    @Test
    void offerExpired_isTerminal() {
        assertThat(ApplicationStatus.OFFER_EXPIRED.isTerminal()).isTrue();
    }

    // ─── Клиентский маппинг ───────────────────────────────────────────────────

    @Test
    void completenessCheck_dataCheck_scoring_haveClientStatus_checkData() {
        assertThat(ApplicationStatus.COMPLETENESS_CHECK.clientStatus()).isEqualTo("Проверка данных");
        assertThat(ApplicationStatus.DATA_CHECK.clientStatus()).isEqualTo("Проверка данных");
        assertThat(ApplicationStatus.SCORING.clientStatus()).isEqualTo("Проверка данных");
    }

    @Test
    void needsInfo_isActionRequired() {
        assertThat(ApplicationStatus.NEEDS_INFO.actionRequired()).isTrue();
    }

    @Test
    void approved_isActionRequired() {
        assertThat(ApplicationStatus.APPROVED.actionRequired()).isTrue();
    }

    @Test
    void manualReview_isNotActionRequired() {
        assertThat(ApplicationStatus.MANUAL_REVIEW.actionRequired()).isFalse();
    }

    @Test
    void signed_and_disbursed_haveClientStatus_issued() {
        assertThat(ApplicationStatus.SIGNED.clientStatus()).isEqualTo("Выдано");
        assertThat(ApplicationStatus.DISBURSED.clientStatus()).isEqualTo("Выдано");
    }

    @Test
    void cancelled_and_offerExpired_areExtensionStatuses() {
        assertThat(ApplicationStatus.CANCELLED.clientStatus()).isEqualTo("Заявка отозвана");
        assertThat(ApplicationStatus.OFFER_EXPIRED.clientStatus()).isEqualTo("Срок предложения истёк");
    }

    // ─── Переходы из пайплайна (DATA_CHECK / SCORING / MANUAL_REVIEW) ─────────

    @Test
    void dataCheck_canGoTo_scoring_manualReview_rejected() {
        Set<ApplicationStatus> expected = Set.of(
                ApplicationStatus.SCORING,
                ApplicationStatus.MANUAL_REVIEW,
                ApplicationStatus.REJECTED);
        assertThat(ApplicationStatus.DATA_CHECK.allowedTargets()).isEqualTo(expected);
    }

    @Test
    void scoring_canGoTo_approved_rejected_manualReview_needsInfo() {
        Set<ApplicationStatus> expected = Set.of(
                ApplicationStatus.APPROVED,
                ApplicationStatus.REJECTED,
                ApplicationStatus.MANUAL_REVIEW,
                ApplicationStatus.NEEDS_INFO);
        assertThat(ApplicationStatus.SCORING.allowedTargets()).isEqualTo(expected);
    }

    @Test
    void manualReview_canGoTo_approved_rejected_needsInfo_cancelled() {
        Set<ApplicationStatus> expected = Set.of(
                ApplicationStatus.APPROVED,
                ApplicationStatus.REJECTED,
                ApplicationStatus.NEEDS_INFO,
                ApplicationStatus.CANCELLED);
        assertThat(ApplicationStatus.MANUAL_REVIEW.allowedTargets()).isEqualTo(expected);
    }

    @Test
    void signed_canGoTo_disbursed() {
        assertThat(ApplicationStatus.SIGNED.canGoTo(ApplicationStatus.DISBURSED)).isTrue();
        assertThat(ApplicationStatus.SIGNED.allowedTargets()).isEqualTo(Set.of(ApplicationStatus.DISBURSED));
    }

    @Test
    void cancelled_isTerminal() {
        assertThat(ApplicationStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.CANCELLED.allowedTargets()).isEmpty();
    }

    @Test
    void submitted_cannotSkipPipeline_to_approved_or_scoring() {
        assertThat(ApplicationStatus.SUBMITTED.canGoTo(ApplicationStatus.APPROVED)).isFalse();
        assertThat(ApplicationStatus.SUBMITTED.canGoTo(ApplicationStatus.SCORING)).isFalse();
        assertThat(ApplicationStatus.SUBMITTED.canGoTo(ApplicationStatus.REJECTED)).isFalse();
    }
}
