package ru.backend.auth.dto;

import ru.backend.session.Role;

import java.util.UUID;

public record LoginResponse(String token, Role role, String fullName, UUID companyId) {}
