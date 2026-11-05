package com.lookupstock.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.exception.ForbiddenException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.UsageMetric;
import com.lookupstock.backend.model.UsageReport;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.model.UserRole;
import com.lookupstock.backend.repository.UsageEventRepository;
import com.lookupstock.backend.repository.UserRepository;

@Service
public class UsageReportService {

    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final UsageEventRepository usageEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public UsageReportService(UsageEventRepository usageEventRepository,
                              UserRepository userRepository,
                              Clock clock) {
        this.usageEventRepository = usageEventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsageReport report(String month, User user) {
        // Check the role first so nothing about the data is revealed without it.
        if (user.getRole() != UserRole.MANAGER) {
            throw new ForbiddenException(Messages.FORBIDDEN);
        }

        Instant now = clock.instant();
        YearMonth currentMonth = YearMonth.from(now.atZone(ZONE));
        YearMonth requested = parseMonth(month, currentMonth);

        LocalDate trackingSince = usageEventRepository.findEarliestOccurredAt()
                .map(first -> LocalDate.ofInstant(first, ZONE))
                .orElseThrow(UsageReportService::notAvailable);
        if (requested.isBefore(YearMonth.from(trackingSince)) || requested.isAfter(currentMonth)) {
            throw notAvailable();
        }

        // Month boundaries in UTC; the time zone handles daylight saving time.
        Instant from = requested.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant to = requested.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        boolean preliminary = requested.equals(currentMonth);

        Map<UsageEventType, Long> counts = new EnumMap<>(UsageEventType.class);
        for (UsageEventRepository.EventTypeCount row : usageEventRepository.countByTypeBetween(from, to)) {
            counts.put(row.getEventType(), row.getTotal());
        }

        Map<UsageMetric, Long> values = new EnumMap<>(UsageMetric.class);
        for (UsageMetric metric : UsageMetric.values()) {
            long value = metric == UsageMetric.TOTAL_ACCOUNTS
                    ? userRepository.countByCreatedAtBefore(preliminary ? now : to)
                    : counts.getOrDefault(metric.getEventType(), 0L);
            values.put(metric, value);
        }
        return new UsageReport(requested, preliminary, trackingSince, values);
    }

    private static YearMonth parseMonth(String month, YearMonth currentMonth) {
        if (month == null || month.isBlank()) {
            return currentMonth;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(Messages.MONTH_FORMAT,
                    List.of(new ValidationException.FieldError("month", Messages.valueDetail(month))));
        }
    }

    private static ValidationException notAvailable() {
        return new ValidationException(Messages.REPORT_NOT_AVAILABLE, List.of());
    }
}
