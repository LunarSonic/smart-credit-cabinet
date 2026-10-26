package ru.backend.company.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AccountStats(int months, BigDecimal avgMonthlyTurnover, BigDecimal avgBalance,
                           BigDecimal trend, int growingMonths, List<Map<String, Object>> monthly) {}
