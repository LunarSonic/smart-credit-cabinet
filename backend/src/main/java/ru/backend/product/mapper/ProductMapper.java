package ru.backend.product.mapper;

import ru.backend.product.dto.PreapprovedOfferDto;
import ru.backend.product.dto.ProductDto;
import ru.backend.product.entity.PreapprovedOffer;
import ru.backend.product.entity.Product;

import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getSuitablePurpose(),
                p.getMinAmount(), p.getMaxAmount(), p.getMinTermMonths(), p.getMaxTermMonths(), p.getBaseRate());
    }

    public PreapprovedOfferDto toDto(PreapprovedOffer o, Product p) {
        return new PreapprovedOfferDto(o.getId(), p.getId(), p.getCode(), p.getName(), o.getMaxAmount(),
                o.getRateFrom(), o.getValidUntil(), o.getReason());
    }
}
