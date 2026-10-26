package ru.backend.product.repository;

import ru.backend.product.entity.PreapprovedOffer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreapprovedOfferRepository extends JpaRepository<PreapprovedOffer, UUID> {
    List<PreapprovedOffer> findByCompanyId(UUID companyId);
    Optional<PreapprovedOffer> findByCompanyIdAndProductId(UUID companyId, UUID productId);
}
