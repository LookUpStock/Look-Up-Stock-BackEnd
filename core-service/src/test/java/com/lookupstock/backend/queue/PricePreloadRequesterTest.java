package com.lookupstock.backend.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.User;

class PricePreloadRequesterTest {

    private static final User OWNER = new User("anna@example.com", "hash");

    private MessagePublisher publisher;
    private PricePreloadRequester requester;

    @BeforeEach
    void setUp() {
        publisher = mock(MessagePublisher.class);
        requester = new PricePreloadRequester(publisher);
    }

    @Test
    void publishesSymbolAndMicOfAllPositions() {
        Portfolio portfolio = new Portfolio("Welt", OWNER);
        portfolio.addPosition(asset(1L, "URTH", "ARCX"), new BigDecimal("70"));
        portfolio.addPosition(asset(2L, "IEMG", "ARCX"), new BigDecimal("30"));

        requester.request(portfolio);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(QueueTopics.PRICE_PRELOAD), payload.capture());
        assertThat(((PricePreloadMessage) payload.getValue()).instruments()).containsExactly(
                new PricePreloadMessage.Instrument("URTH", "ARCX"),
                new PricePreloadMessage.Instrument("IEMG", "ARCX"));
    }

    @Test
    void portfolioWithoutPositionsPublishesNothing() {
        requester.request(new Portfolio("Leer", OWNER));

        verifyNoInteractions(publisher);
    }

    @Test
    void failingQueueDoesNotBreakTheAction() {
        Portfolio portfolio = new Portfolio("Welt", OWNER);
        portfolio.addPosition(asset(1L, "URTH", "ARCX"), new BigDecimal("100"));
        doThrow(new IllegalStateException("Datenbank nicht erreichbar")).when(publisher).publish(anyString(), any());

        assertThatCode(() -> requester.request(portfolio)).doesNotThrowAnyException();
    }

    private static Asset asset(Long id, String symbol, String mic) {
        Asset asset = new Asset(symbol, null, symbol + " ETF", "NYSE", "USD", "ETF", mic);
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
