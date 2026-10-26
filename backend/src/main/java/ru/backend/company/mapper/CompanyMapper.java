package ru.backend.company.mapper;

import ru.backend.company.dto.CompanyDto;
import ru.backend.company.entity.Company;

import org.springframework.stereotype.Component;

@Component
public class CompanyMapper {
    public CompanyDto toDto(Company c) {
        return new CompanyDto(c.getId(), c.getInn(), c.getName(), c.getOkved(), c.getDirectorName(), c.getRegisteredAt());
    }
}
