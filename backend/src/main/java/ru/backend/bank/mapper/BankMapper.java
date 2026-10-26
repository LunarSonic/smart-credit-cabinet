package ru.backend.bank.mapper;

import ru.backend.bank.dto.ManualDecisionDto;
import ru.backend.bank.entity.ManualDecision;

import org.springframework.stereotype.Component;

@Component
public class BankMapper {
    public ManualDecisionDto toDto(ManualDecision m) {
        return new ManualDecisionDto(m.getId(), m.getEmployeeId(), m.getAction(), m.getComment(), m.getCreatedAt());
    }
}
