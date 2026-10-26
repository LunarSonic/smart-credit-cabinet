package ru.backend.product.repository;

import ru.backend.product.entity.Product;
import ru.backend.product.entity.enums.Purpose;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByActiveTrueOrderByName();
    List<Product> findByActiveTrueAndPreapprovableTrue();
    Optional<Product> findFirstBySuitablePurposeAndActiveTrue(Purpose purpose);
}
