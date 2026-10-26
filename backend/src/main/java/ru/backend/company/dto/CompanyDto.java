package ru.backend.company.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CompanyDto(UUID id, String inn, String name, String okved, String directorName, LocalDate registeredAt) {}
