package ru.backend.product.controller;

import ru.backend.product.dto.PreapprovedOfferDto;
import ru.backend.product.dto.ProductDto;
import ru.backend.product.dto.RecommendationDto;
import ru.backend.product.dto.RecommendationRequest;
import ru.backend.product.entity.Product;
import ru.backend.product.mapper.ProductMapper;
import ru.backend.product.repository.ProductRepository;
import ru.backend.product.service.PreapprovalService;
import ru.backend.product.service.RecommendationService;
import ru.backend.session.AuthUser;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Этап 1: витрина продуктов, предодобрения, подбор продукта */

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductRepository products;
    private final PreapprovalService preapproval;
    private final RecommendationService recommendations;
    private final ProductMapper mapper;

    public ProductController(ProductRepository products, PreapprovalService preapproval,
                             RecommendationService recommendations, ProductMapper mapper) {
        this.products = products; this.preapproval = preapproval; this.recommendations = recommendations; this.mapper = mapper;
    }

    @GetMapping("/products")
    public List<ProductDto> products() {
        return products.findByActiveTrueOrderByName().stream().map(p -> mapper.toDto(p)).toList();
    }

    @GetMapping("/offers/preapproved")
    public List<PreapprovedOfferDto> preapproved(@AuthenticationPrincipal AuthUser u) {
        if (u.companyId() == null) return List.of();
        preapproval.refresh(u.companyId());
        return preapproval.current(u.companyId()).stream().map(o -> {
            Product p = products.findById(o.getProductId()).orElseThrow();
            return mapper.toDto(o, p);
        }).toList();
    }

    @PostMapping("/recommendations")
    public RecommendationDto recommend(@Valid @RequestBody RecommendationRequest r) {
        return recommendations.recommend(r);
    }
}
