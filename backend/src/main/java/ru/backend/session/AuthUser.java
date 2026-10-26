package ru.backend.session;

import java.util.UUID;

public record AuthUser(UUID id, Role role, UUID companyId, String login) {}
