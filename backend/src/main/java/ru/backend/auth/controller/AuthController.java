package ru.backend.auth.controller;

import ru.backend.auth.dto.LoginRequest;
import ru.backend.auth.dto.LoginResponse;
import ru.backend.auth.entity.AppUser;
import ru.backend.auth.repository.AppUserRepository;
import ru.backend.common.BusinessException;
import ru.backend.product.service.PreapprovalService;
import ru.backend.session.AuthUser;
import ru.backend.session.JwtService;
import ru.backend.session.Role;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PreapprovalService preapproval;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, PreapprovalService preapproval) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.preapproval = preapproval;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        AppUser u = users.findByLogin(r.login())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль"));
        if (u.getRole() == Role.CLIENT && u.getCompanyId() != null) {
            try {
                preapproval.refresh(u.getCompanyId());   // предодобрения считаются при входе
            } catch (Exception e) {
                log.warn("Не удалось пересчитать предодобрения для {}: {}", u.getLogin(), e.getMessage());
            }
        }
        AuthUser identity = new AuthUser(u.getId(), u.getRole(), u.getCompanyId(), u.getLogin());
        return new LoginResponse(jwt.issue(identity), u.getRole(), u.getFullName(), u.getCompanyId());
    }

    @GetMapping("/me")
    public AuthUser me(@AuthenticationPrincipal AuthUser user) { return user; }
}
