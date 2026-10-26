package ru.backend.auth.service;

import ru.backend.auth.entity.AppUser;
import ru.backend.auth.repository.AppUserRepository;
import ru.backend.company.repository.CompanyRepository;
import ru.backend.session.Role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Демо-пользователи: client1..client6 (по компании на каждый сценарий) и employee */

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder encoder;
    private final String password;

    public DataSeeder(AppUserRepository users, CompanyRepository companies, PasswordEncoder encoder,
                      @Value("${app.seed.demo-password}") String password) {
        this.users = users; this.companies = companies; this.encoder = encoder; this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int i = 1; i <= 6; i++) {
            String login = "client" + i;
            String inn = String.format("77010000%04d", i);
            if (users.findByLogin(login).isPresent()) continue;
            companies.findByInn(inn).ifPresent(c -> {
                AppUser u = new AppUser();
                u.setLogin(login); u.setRole(Role.CLIENT); u.setCompanyId(c.getId());
                u.setFullName("Представитель: " + c.getName());
                u.setPasswordHash(encoder.encode(password));
                users.save(u);
            });
        }
        if (users.findByLogin("employee").isEmpty()) {
            AppUser e = new AppUser();
            e.setLogin("employee"); e.setRole(Role.EMPLOYEE); e.setFullName("Сотрудник банка (демо)");
            e.setPasswordHash(encoder.encode(password));
            users.save(e);
        }
        log.info("Демо-пользователи готовы: client1..client6, employee");
    }
}
