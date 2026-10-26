package ru.backend.product.dto;

import java.util.List;

public record RecommendationDto(ProductDto product, String explanation, List<String> warnings) {}
