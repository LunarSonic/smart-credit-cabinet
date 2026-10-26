package ru.backend.offer.repository;

import ru.backend.offer.entity.Contract;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Optional<Contract> findByOfferId(UUID offerId);
    List<Contract> findByDisbursedAtIsNullAndSignedAtBefore(Instant before);

    @Query("select c from Contract c where c.offerId in (select o.id from Offer o where o.applicationId in " +
           "(select a.id from Application a where a.companyId = :companyId)) order by c.signedAt desc")
    List<Contract> findByCompany(@Param("companyId") UUID companyId);
}
