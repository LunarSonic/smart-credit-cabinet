package ru.backend.application.check;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MockBkiRepository extends JpaRepository<MockBkiRecord, String> {
}
