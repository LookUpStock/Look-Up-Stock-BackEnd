package com.lookupstock.marktdaten.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lookupstock.marktdaten.exception.UpstreamUnavailableException;
import com.lookupstock.marktdaten.service.PriceService;

import tools.jackson.databind.json.JsonMapper;

class PricePreloadConsumerTest {

    private QueueRepository queue;
    private PriceService priceService;
    private PricePreloadConsumer consumer;

    @BeforeEach
    void setUp() {
        queue = mock(QueueRepository.class);
        priceService = mock(PriceService.class);
        consumer = new PricePreloadConsumer(queue, priceService, JsonMapper.builder().build());
    }

    @Test
    void loadsPricesOfAllInstrumentsAndDeletesMessage() {
        when(queue.lockNextBatch("kursdaten.vorladen", PricePreloadConsumer.BATCH_SIZE)).thenReturn(List.of(entry(7,
                "{\"instruments\":[{\"symbol\":\"URTH\",\"micCode\":\"ARCX\"},{\"symbol\":\"IEMG\",\"micCode\":null}]}")));

        consumer.processBatch();

        verify(priceService).getPrices("URTH", "ARCX", "1month", 120);
        verify(priceService).getPrices("IEMG", null, "1month", 120);
        verify(queue).delete(7L);
        verify(queue, never()).registerFailure(anyLong(), any(), anyInt());
    }

    @Test
    void upstreamUnavailableKeepsMessageForRetry() {
        when(queue.lockNextBatch(anyString(), anyInt())).thenReturn(List.of(entry(8,
                "{\"instruments\":[{\"symbol\":\"URTH\",\"micCode\":\"ARCX\"}]}")));
        when(priceService.getPrices("URTH", "ARCX", "1month", 120)).thenThrow(new UpstreamUnavailableException(null));

        consumer.processBatch();

        verify(queue).registerFailure(eq(8L), eq(UpstreamUnavailableException.MESSAGE), eq(PricePreloadConsumer.MAX_ATTEMPTS));
        verify(queue, never()).delete(anyLong());
    }

    @Test
    void invalidPayloadIsRegisteredAsFailure() {
        when(queue.lockNextBatch(anyString(), anyInt())).thenReturn(List.of(entry(9, "{\"falsch\":true}")));

        consumer.processBatch();

        verify(queue).registerFailure(eq(9L), any(), eq(PricePreloadConsumer.MAX_ATTEMPTS));
        verifyNoInteractions(priceService);
    }

    @Test
    void oneFailingMessageDoesNotStopTheOthers() {
        when(queue.lockNextBatch(anyString(), anyInt())).thenReturn(List.of(
                entry(10, "kein json"),
                entry(11, "{\"instruments\":[{\"symbol\":\"URTH\",\"micCode\":\"ARCX\"}]}")));

        consumer.processBatch();

        verify(queue).registerFailure(eq(10L), any(), anyInt());
        verify(queue).delete(11L);
    }

    @Test
    void emptyQueueDoesNothing() {
        when(queue.lockNextBatch(anyString(), anyInt())).thenReturn(List.of());

        consumer.processBatch();

        verifyNoInteractions(priceService);
        verify(queue, never()).delete(anyLong());
    }

    private static QueueRepository.QueueEntry entry(long id, String payload) {
        return new QueueRepository.QueueEntry(id, payload);
    }
}
