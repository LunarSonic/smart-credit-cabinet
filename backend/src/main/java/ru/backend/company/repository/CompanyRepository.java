package ru.backend.company.repository;

import ru.backend.company.entity.Company;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByInn(String inn);
}
