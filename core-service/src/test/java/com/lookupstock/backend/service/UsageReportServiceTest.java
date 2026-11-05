package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

class UsageReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private static final Instant FIRST_EVENT = Instant.parse("2026-10-31T09:00:00Z");

    private UsageEventRepository usageEventRepository;
    private UserRepository userRepository;
    private UsageReportService service;
    private User manager;
    private User normalUser;

    private record Row(UsageEventType eventType, long total) implements UsageEventRepository.EventTypeCount {
        @Override public UsageEventType getEventType() { return eventType; }
        @Override public long getTotal() { return total; }
    }

    @BeforeEach
    void setUp() {
        usageEventRepository = mock(UsageEventRepository.class);
        userRepository = mock(UserRepository.class);
        service = new UsageReportService(usageEventRepository, userRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        manager = new User("chefin@example.com", "hash");
        ReflectionTestUtils.setField(manager, "role", UserRole.MANAGER);
        normalUser = new User("anna@example.com", "hash");

        when(usageEventRepository.findEarliestOccurredAt()).thenReturn(Optional.of(FIRST_EVENT));
        when(usageEventRepository.countByTypeBetween(any(), any())).thenReturn(List.of());
    }

    @Test
    void ohneRolleGeschaeftsfuehrungLiefert403UndLiestNichts() {
        assertThatThrownBy(() -> service.report("2026-10", normalUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(Messages.FORBIDDEN);
        verifyNoInteractions(usageEventRepository, userRepository);
    }

    @Test
    void ohneMonatWirdDerLaufendeMonatVorlaeufigAusgewertet() {
        when(userRepository.countByCreatedAtBefore(NOW)).thenReturn(38L);

        UsageReport report = service.report(null, manager);

        assertThat(report.month()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(report.preliminary()).isTrue();
        assertThat(report.trackingSince()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(report.values().get(UsageMetric.TOTAL_ACCOUNTS)).isEqualTo(38L);
    }

    @Test
    void abgeschlossenerMonatNutztMonatsgrenzenNachDeutscherZeit() {
        Instant from = Instant.parse("2026-09-30T22:00:00Z");
        Instant to = Instant.parse("2026-10-31T23:00:00Z");
        when(usageEventRepository.countByTypeBetween(from, to)).thenReturn(List.of(
                new Row(UsageEventType.LOGIN, 27),
                new Row(UsageEventType.SIMULATION_RUN, 41)));
        when(userRepository.countByCreatedAtBefore(to)).thenReturn(30L);

        UsageReport report = service.report("2026-10", manager);

        assertThat(report.preliminary()).isFalse();
        verify(usageEventRepository).countByTypeBetween(from, to);
        assertThat(report.values().get(UsageMetric.LOGINS)).isEqualTo(27L);
        assertThat(report.values().get(UsageMetric.SIMULATIONS_RUN)).isEqualTo(41L);
        assertThat(report.values().get(UsageMetric.TOTAL_ACCOUNTS)).isEqualTo(30L);
    }

    @Test
    void liefertAlleNeunKennzahlenInFesterReihenfolgeUndNullOhneEreignis() {
        UsageReport report = service.report("2026-10", manager);

        assertThat(report.values().keySet()).containsExactly(UsageMetric.values());
        assertThat(report.values()).hasSize(9);
        assertThat(report.values().get(UsageMetric.REGISTRATIONS)).isZero();
    }

    @Test
    void monatVorBeginnDerErfassungLiefert400() {
        assertThatThrownBy(() -> service.report("2026-09", manager))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.REPORT_NOT_AVAILABLE);
    }

    @Test
    void kuenftigerMonatLiefert400() {
        assertThatThrownBy(() -> service.report("2026-12", manager))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.REPORT_NOT_AVAILABLE);
    }

    @Test
    void ohneErfassteEreignisseLiefertJederMonat400() {
        when(usageEventRepository.findEarliestOccurredAt()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(null, manager))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.REPORT_NOT_AVAILABLE);
    }

    @Test
    void falschesFormatLiefert400MitFeldMonth() {
        assertThatThrownBy(() -> service.report("10-2026", manager))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.MONTH_FORMAT)
                .satisfies(e -> assertThat(((ValidationException) e).getErrors())
                        .containsExactly(new ValidationException.FieldError("month", "Wert: 10-2026")));
    }
}
