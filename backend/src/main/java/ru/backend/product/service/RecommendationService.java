package ru.backend.product.service;

import ru.backend.common.Fmt;
import ru.backend.common.NotFoundException;
import ru.backend.product.dto.RecommendationDto;
import ru.backend.product.dto.RecommendationRequest;
import ru.backend.product.entity.Product;
import ru.backend.product.entity.enums.Purpose;
import ru.backend.product.mapper.ProductMapper;
import ru.backend.product.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Этап 1, «свой запрос»: цель/сумма/срок -> продукт и объяснение выбора (правила, без ML) */

@Service
public class RecommendationService {
    private final ProductRepository products;
    private final ProductMapper mapper;

    public RecommendationService(ProductRepository products, ProductMapper mapper) {
        this.products = products; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public RecommendationDto recommend(RecommendationRequest r) {
        Product p = products.findFirstBySuitablePurposeAndActiveTrue(r.purpose())
                .orElseThrow(() -> new NotFoundException("Для цели «" + r.purpose().label() + "» нет подходящего продукта"));

        List<String> warnings = new ArrayList<>();
        if (r.amount().compareTo(p.getMaxAmount()) > 0) {
            warnings.add("Запрошенная сумма выше лимита продукта (" + Fmt.money(p.getMaxAmount()) + ")");
        }
        if (r.amount().compareTo(p.getMinAmount()) < 0) {
            warnings.add("Запрошенная сумма ниже минимума по продукту (" + Fmt.money(p.getMinAmount()) + ")");
        }
        if (r.termMonths() > p.getMaxTermMonths() || r.termMonths() < p.getMinTermMonths()) {
            warnings.add("Срок вне границ продукта: от " + p.getMinTermMonths() + " до " + p.getMaxTermMonths() + " мес.");
        }
        return new RecommendationDto(mapper.toDto(p), explain(r.purpose(), p), warnings);
    }

    private String explain(Purpose purpose, Product p) {
        String term = p.getMaxTermMonths() % 12 == 0 ? "до " + (p.getMaxTermMonths() / 12) + " лет" : "до " + p.getMaxTermMonths() + " мес.";
        return switch (purpose) {
            case EQUIPMENT -> "Для оборудования подходит «" + p.getName() + "», срок " + term + ": платежи растягиваются на период использования техники.";
            case WORKING_CAPITAL -> "Для пополнения оборотки подходит «" + p.getName() + "», срок " + term + ": деньги на закупки и текущие расходы.";
            case CASH_GAP -> "Для кассового разрыва подходит «" + p.getName() + "», срок " + term + ": платите проценты только за дни использования лимита.";
            case REAL_ESTATE -> "Для коммерческой недвижимости подходит «" + p.getName() + "», срок " + term + ".";
            case REFINANCING -> "Для рефинансирования подходит «" + p.getName() + "», срок " + term + ".";
            case OTHER -> "Для выбранной цели подходит «" + p.getName() + "», срок " + term + ".";
        };
    }

}
