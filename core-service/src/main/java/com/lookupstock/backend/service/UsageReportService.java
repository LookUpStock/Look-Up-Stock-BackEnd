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

/**
 * Auswertung der Nutzungsereignisse je Kalendermonat (US-11, BE #111).
 * Nur für die Rolle Geschäftsführung (E40); Monate nach deutscher Zeit (E38).
 */
@Service
public class UsageReportService {

    // Kalendermonate nach deutscher Zeit (E38)
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

    /**
     * Kennzahlen eines Monats.
     *
     * @param month Monat im Format JJJJ-MM; null oder leer = laufender Monat
     * @param user  angemeldeter Nutzer, braucht die Rolle MANAGER (sonst 403)
     * @throws ForbiddenException  ohne die Rolle Geschäftsführung (E40)
     * @throws ValidationException bei falschem Format oder einem Monat ohne Auswertung (E38)
     */
    @Transactional(readOnly = true)
    public UsageReport report(String month, User user) {
        // 1. Berechtigung zuerst: ohne Rolle wird nichts über die Daten verraten (E40)
        if (user.getRole() != UserRole.MANAGER) {
            throw new ForbiddenException(Messages.FORBIDDEN);
        }

        // 2. Monat bestimmen und prüfen (E38)
        Instant now = clock.instant();
        YearMonth currentMonth = YearMonth.from(now.atZone(ZONE));
        YearMonth requested = parseMonth(month, currentMonth);

        LocalDate trackingSince = usageEventRepository.findEarliestOccurredAt()
                .map(first -> LocalDate.ofInstant(first, ZONE))
                .orElseThrow(UsageReportService::notAvailable);
        if (requested.isBefore(YearMonth.from(trackingSince)) || requested.isAfter(currentMonth)) {
            throw notAvailable();
        }

        // 3. Monatsgrenzen in UTC; Sommer- und Winterzeit übernimmt die Zeitzone
        Instant from = requested.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant to = requested.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        boolean preliminary = requested.equals(currentMonth);

        // 4. Ereignisse je Art zählen; Arten ohne Ereignis bleiben 0
        Map<UsageEventType, Long> counts = new EnumMap<>(UsageEventType.class);
        for (UsageEventRepository.EventTypeCount row : usageEventRepository.countByTypeBetween(from, to)) {
            counts.put(row.getEventType(), row.getTotal());
        }

        // 5. Neun Kennzahlen in fester Reihenfolge (E39); Konten am Monatsende bzw. jetzt im laufenden Monat
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
            // YearMonth.parse erwartet genau JJJJ-MM
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
