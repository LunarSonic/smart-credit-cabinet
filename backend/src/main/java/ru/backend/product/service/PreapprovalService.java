package ru.backend.product.service;

import ru.backend.common.Fmt;
import ru.backend.common.NotFoundException;
import ru.backend.company.entity.Company;
import ru.backend.company.repository.CompanyRepository;
import ru.backend.company.service.AccountAnalytics;
import ru.backend.company.service.AccountStats;
import ru.backend.product.entity.PreapprovedOffer;
import ru.backend.product.entity.Product;
import ru.backend.product.repository.PreapprovedOfferRepository;
import ru.backend.product.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Умность» кабинета, этап 1: предодобренные предложения. Это наши бизнес-правила
 * Правило: обороты растут >= 3 месяцев подряд и бизнесу >= 12 месяцев; лимит = 55% средних оборотов, округлённо до 10 тыс
 */

@Service
public class PreapprovalService {
    private static final BigDecimal SHARE_OF_TURNOVER = new BigDecimal("0.55");
    private static final BigDecimal STEP = new BigDecimal("10000");

    private final ProductRepository products;
    private final CompanyRepository companies;
    private final PreapprovedOfferRepository offers;
    private final AccountAnalytics analytics;

    public PreapprovalService(ProductRepository products, CompanyRepository companies,
                              PreapprovedOfferRepository offers, AccountAnalytics analytics) {
        this.products = products; this.companies = companies; this.offers = offers; this.analytics = analytics;
    }

    @Transactional
    public List<PreapprovedOffer> refresh(UUID companyId) {
        Company c = companies.findById(companyId).orElseThrow(() -> new NotFoundException("Компания не найдена"));
        AccountStats stats = analytics.stats(companyId);
        long ageMonths = ChronoUnit.MONTHS.between(c.getRegisteredAt(), LocalDate.now());
        List<PreapprovedOffer> result = new ArrayList<>();

        for (Product p : products.findByActiveTrueAndPreapprovableTrue()) {
            Optional<PreapprovedOffer> existing = offers.findByCompanyIdAndProductId(companyId, p.getId());
            BigDecimal max = eligibleAmount(p, stats, ageMonths);
            if (max == null) {
                existing.ifPresent(offers::delete);
                continue;
            }
            PreapprovedOffer o = existing.orElseGet(PreapprovedOffer::new);
            o.setCompanyId(companyId);
            o.setProductId(p.getId());
            o.setMaxAmount(max);
            o.setRateFrom(p.getBaseRate());
            o.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("code", "TURNOVER_GROWTH");
            reason.put("growing_months", stats.growingMonths());
            reason.put("avg_monthly_turnover", stats.avgMonthlyTurnover());
            reason.put("text", "Обороты по счёту стабильно растут " + stats.growingMonths()
                    + " мес. подряд, средние обороты — " + Fmt.money(stats.avgMonthlyTurnover()) + " в месяц");
            o.setReason(reason);
            result.add(offers.save(o));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PreapprovedOffer> current(UUID companyId) {
        return offers.findByCompanyId(companyId).stream()
                .filter(o -> o.getValidUntil().isAfter(Instant.now())).toList();
    }

    private BigDecimal eligibleAmount(Product p, AccountStats s, long ageMonths) {
        if (s.growingMonths() < 3 || ageMonths < 12) return null;
        BigDecimal raw = s.avgMonthlyTurnover().multiply(SHARE_OF_TURNOVER);
        BigDecimal rounded = raw.divide(STEP, 0, RoundingMode.DOWN).multiply(STEP);
        BigDecimal capped = rounded.min(p.getMaxAmount());
        return capped.compareTo(p.getMinAmount()) < 0 ? null : capped;
    }
}
