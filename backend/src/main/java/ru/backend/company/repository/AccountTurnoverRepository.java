package ru.backend.company.repository;

import ru.backend.company.entity.AccountTurnover;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTurnoverRepository extends JpaRepository<AccountTurnover, UUID> {
    List<AccountTurnover> findTop6ByCompanyIdOrderByMonthDesc(UUID companyId);
}
