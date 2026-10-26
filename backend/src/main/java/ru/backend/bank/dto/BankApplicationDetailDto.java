package ru.backend.bank.dto;

import ru.backend.application.dto.ApplicationDetailDto;
import ru.backend.application.dto.CheckDto;
import ru.backend.company.dto.CompanyDto;
import ru.backend.offer.dto.OfferDto;

import java.util.List;
import java.util.Map;

public record BankApplicationDetailDto(ApplicationDetailDto detail, OfferDto offer, CompanyDto company,
                                           List<CheckDto> checks, Map<String, Object> decision,
                                           List<ManualDecisionDto> manualDecisions) {}
