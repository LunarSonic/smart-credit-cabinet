package ru.backend.application.check;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MockEgrulRepository extends JpaRepository<MockEgrulRecord, String> {
}
