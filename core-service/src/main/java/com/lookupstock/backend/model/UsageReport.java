package com.lookupstock.backend.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

public record UsageReport(YearMonth month, boolean preliminary, LocalDate trackingSince,
                          Map<UsageMetric, Long> values) {
}
