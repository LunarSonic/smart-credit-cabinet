package ru.backend.offer.repository;

import ru.backend.offer.entity.Offer;
import ru.backend.offer.entity.enums.OfferStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, UUID> {
    List<Offer> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
    List<Offer> findByApplicationIdAndStatus(UUID applicationId, OfferStatus status);
    List<Offer> findByStatusAndValidUntilBefore(OfferStatus status, Instant before);
}
