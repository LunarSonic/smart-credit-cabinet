package ru.backend.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.backend.application.dto.*;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.Consent;
import ru.backend.application.entity.enums.ConsentType;
import ru.backend.application.repository.ApplicationIssueRepository;
import ru.backend.application.repository.CheckResultRepository;
import ru.backend.application.repository.ConsentRepository;
import ru.backend.application.repository.DeclaredObligationRepository;
import ru.backend.application.repository.StatusHistoryRepository;
import ru.backend.bank.dto.BankApplicationDetailDto;
import ru.backend.bank.dto.ManualDecisionDto;
import ru.backend.bank.repository.ManualDecisionRepository;
import ru.backend.common.NotFoundException;
import ru.backend.company.dto.CompanyDto;
import ru.backend.company.entity.Company;
import ru.backend.company.repository.CompanyRepository;
import ru.backend.decision.entity.DecisionSnapshot;
import ru.backend.decision.repository.DecisionSnapshotRepository;
import ru.backend.offer.dto.OfferDto;
import ru.backend.offer.entity.Offer;
import ru.backend.offer.mapper.OfferMapper;
import ru.backend.offer.repository.OfferRepository;
import ru.backend.product.entity.Product;
import ru.backend.product.repository.ProductRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
@Transactional(readOnly = true)
public class ApplicationQueryService {

    private final ProductRepository products;
    private final CompanyRepository companies;
    private final DeclaredObligationRepository obligations;
    private final ApplicationIssueRepository issues;
    private final StatusHistoryRepository history;
    private final OfferRepository offers;
    private final ConsentRepository consents;
    private final CheckResultRepository checks;
    private final DecisionSnapshotRepository snapshots;
    private final ManualDecisionRepository manualDecisions;
    private final OfferMapper offerMapper;

    public ApplicationQueryService(ProductRepository products, CompanyRepository companies,
                                   DeclaredObligationRepository obligations,
                                   ApplicationIssueRepository issues,
                                   StatusHistoryRepository history,
                                   OfferRepository offers,
                                   ConsentRepository consents,
                                   CheckResultRepository checks,
                                   DecisionSnapshotRepository snapshots,
                                   ManualDecisionRepository manualDecisions,
                                   OfferMapper offerMapper) {
        this.products       = products;
        this.companies      = companies;
        this.obligations    = obligations;
        this.issues         = issues;
        this.history        = history;
        this.offers         = offers;
        this.consents       = consents;
        this.checks         = checks;
        this.snapshots      = snapshots;
        this.manualDecisions = manualDecisions;
        this.offerMapper    = offerMapper;
    }

    public ApplicationSummaryDto summary(Application a) {
        Product p = products.findById(a.getProductId())
                .orElseThrow(() -> new NotFoundException("Продукт не найден"));
        Company c = companies.findById(a.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Компания не найдена"));
        return new ApplicationSummaryDto(
                a.getId(), c.getId(), c.getName(),
                p.getCode(), p.getName(),
                a.getAmount(), a.getTermMonths(),
                a.getPurpose(), a.getPurpose() == null ? null : a.getPurpose().label(),
                a.getStatus(),
                a.getStatus().clientLabel(),
                a.getStatus().clientStatus(),
                a.getStatus().actionRequired(),
                a.getVersion(),
                a.getCreatedAt(), a.getSubmittedAt(), a.getUpdatedAt());
    }

    public ApplicationDetailDto detail(Application a) {
        List<ObligationDto> obl = obligations.findByApplicationId(a.getId()).stream()
                .map(o -> new ObligationDto(o.getCreditorName(), o.getMonthlyPayment(), o.getRemainingDebt()))
                .toList();

        List<IssueDto> iss = issues.findByApplicationIdOrderByCreatedAtAsc(a.getId()).stream()
                .map(i -> new IssueDto(i.getId(), i.getSource(), i.getField(),
                        i.getMessage(), i.getReason(), i.isResolved(), i.getCreatedAt()))
                .toList();

        List<HistoryDto> hist = history.findByApplicationIdOrderByIdAsc(a.getId()).stream()
                .map(h -> new HistoryDto(h.getId(), h.getFromStatus(), h.getToStatus(),
                        h.getToStatus().clientLabel(), h.getActorType(), h.getActorId(),
                        h.getReason(), h.getComment(), h.getCreatedAt()))
                .toList();

        List<Offer> offerList = offers.findByApplicationIdOrderByCreatedAtDesc(a.getId());
        OfferDto offer = offerList.isEmpty() ? null : offerMapper.toDto(offerList.get(0));

        List<ConsentType> given = consents.findByApplicationId(a.getId())
                .stream().map(Consent::getType).toList();

        List<String> clientReasons = snapshots
                .findFirstByApplicationIdOrderByCreatedAtDesc(a.getId())
                .map(s -> {
                    Object cr = s.getPayload().get("client_reasons");
                    if (cr instanceof List<?> list) {
                        return list.stream().map(Object::toString).toList();
                    }
                    return null;
                }).orElse(null);

        return new ApplicationDetailDto(
                summary(a),
                a.getFullName(), a.getPhone(), a.getRevenue6m(), a.getMonthlyExpenses(),
                obl, iss, hist, offer, given, clientReasons);
    }

    /** Список чек-результатов для банковской карточки. */
    public List<CheckDto> checks(UUID applicationId) {
        return checks.findByApplicationIdOrderByCheckedAtAsc(applicationId).stream()
                .map(r -> new CheckDto(r.getId(), r.getCheckType(), r.getStatus(),
                        r.getPayload(), r.getCheckedAt()))
                .toList();
    }

    public BankApplicationDetailDto bankDetail(Application a) {
        Company c = companies.findById(a.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Компания не найдена"));
        CompanyDto company = new CompanyDto(c.getId(), c.getInn(), c.getName(),
                c.getOkved(), c.getDirectorName(), c.getRegisteredAt());

        Map<String, Object> decision = snapshots
                .findFirstByApplicationIdOrderByCreatedAtDesc(a.getId())
                .map(DecisionSnapshot::getPayload).orElse(null);

        List<ManualDecisionDto> md = manualDecisions
                .findByApplicationIdOrderByCreatedAtAsc(a.getId()).stream()
                .map(m -> new ManualDecisionDto(m.getId(), m.getEmployeeId(),
                        m.getAction(), m.getComment(), m.getCreatedAt()))
                .toList();

        List<Offer> offerList = offers.findByApplicationIdOrderByCreatedAtDesc(a.getId());
        OfferDto offer = offerList.isEmpty() ? null : offerMapper.toDto(offerList.get(0));

        return new BankApplicationDetailDto(detail(a), offer, company, checks(a.getId()), decision, md);
    }
}
