package ru.backend.company.controller;

import ru.backend.common.BusinessException;
import ru.backend.common.NotFoundException;
import ru.backend.company.dto.CompanyDto;
import ru.backend.company.mapper.CompanyMapper;
import ru.backend.company.repository.CompanyRepository;
import ru.backend.session.AuthUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
public class CompanyController {
    private final CompanyRepository companies;
    private final CompanyMapper mapper;

    public CompanyController(CompanyRepository companies, CompanyMapper mapper) {
        this.companies = companies; this.mapper = mapper;
    }

    @GetMapping
    public CompanyDto company(@AuthenticationPrincipal AuthUser u) {
        if (u.companyId() == null) throw new BusinessException(HttpStatus.FORBIDDEN, "У пользователя нет привязки к компании");
        return mapper.toDto(companies.findById(u.companyId()).orElseThrow(() -> new NotFoundException("Компания не найдена")));
    }
}
