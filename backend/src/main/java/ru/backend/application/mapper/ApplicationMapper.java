package ru.backend.application.mapper;

import ru.backend.application.dto.ApplicationSummaryDto;
import ru.backend.application.dto.CheckDto;
import ru.backend.application.dto.HistoryDto;
import ru.backend.application.dto.IssueDto;
import ru.backend.application.dto.ObligationDto;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.ApplicationIssue;
import ru.backend.application.entity.ApplicationStatusHistory;
import ru.backend.application.entity.CheckResult;
import ru.backend.application.entity.DeclaredObligation;
import ru.backend.company.entity.Company;
import ru.backend.product.entity.Product;

import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {
    public ApplicationSummaryDto toSummary(Application a, Product p, Company c) {
        return new ApplicationSummaryDto(a.getId(), c.getId(), c.getName(), p.getCode(), p.getName(), a.getAmount(),
                a.getTermMonths(), a.getPurpose(), a.getPurpose() == null ? null : a.getPurpose().label(),
                a.getStatus(), a.getStatus().clientLabel(), a.getStatus().clientStatus(), a.getStatus().actionRequired(),
                a.getVersion(), a.getCreatedAt(), a.getSubmittedAt(), a.getUpdatedAt());
    }

    public ObligationDto toDto(DeclaredObligation o) {
        return new ObligationDto(o.getCreditorName(), o.getMonthlyPayment(), o.getRemainingDebt());
    }

    public IssueDto toDto(ApplicationIssue i) {
        return new IssueDto(i.getId(), i.getSource(), i.getField(), i.getMessage(), i.getReason(),
                i.isResolved(), i.getCreatedAt());
    }

    public HistoryDto toDto(ApplicationStatusHistory h) {
        return new HistoryDto(h.getId(), h.getFromStatus(), h.getToStatus(), h.getToStatus().clientLabel(),
                h.getActorType(), h.getActorId(), h.getReason(), h.getComment(), h.getCreatedAt());
    }

    public CheckDto toDto(CheckResult r) {
        return new CheckDto(r.getId(), r.getCheckType(), r.getStatus(), r.getPayload(), r.getCheckedAt());
    }
}