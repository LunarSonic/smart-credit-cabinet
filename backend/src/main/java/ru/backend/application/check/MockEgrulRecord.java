package ru.backend.application.check;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mock_egrul")
@Getter
@Setter
@NoArgsConstructor
public class MockEgrulRecord {
    @Id
    private String inn;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String okved;

    @Column(nullable = false)
    private String directorName;

    @Column(nullable = false)
    private LocalDate registeredAt;
}
