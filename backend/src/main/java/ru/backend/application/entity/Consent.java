package ru.backend.application.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.backend.application.entity.enums.ConsentType;

@Entity
@Table(name = "consent")
@Getter
@Setter
@NoArgsConstructor
public class Consent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentType type;

    @Column(nullable = false)
    private String textVersion;

    @Column(nullable = false)
    private UUID givenBy;

    @Column(nullable = false)
    private Instant givenAt = Instant.now();
}
