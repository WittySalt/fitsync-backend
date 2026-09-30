package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProgressInsightPoint(LocalDate date, BigDecimal weightKg) {
}
