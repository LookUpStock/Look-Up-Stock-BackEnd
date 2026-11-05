package com.lookupstock.marktdaten.queue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.marktdaten.service.PriceService;

import tools.jackson.databind.ObjectMapper;

// Few messages per run: every asset without a fresh cache costs a Twelve Data request (free plan: 8 per minute).
@Component
public class PricePreloadConsumer {

    private static final Logger log = LoggerFactory.getLogger(PricePreloadConsumer.class);

    static final String TOPIC = "kursdaten.vorladen";
    static final String MONTHLY_INTERVAL = "1month";
    static final int MONTHS_TO_LOAD = 120;
    static final int BATCH_SIZE = 3;
    static final int MAX_ATTEMPTS = 3;

    private final QueueRepository queueRepository;
    private final PriceService priceService;
    private final ObjectMapper objectMapper;

    public PricePreloadConsumer(QueueRepository queueRepository, PriceService priceService, ObjectMapper objectMapper) {
        this.queueRepository = queueRepository;
        this.priceService = priceService;
        this.objectMapper = objectMapper;
    }

    // One run per transaction so the lock from lockNextBatch is held until the end.
    @Scheduled(fixedDelayString = "${app.queue.preload-interval-ms:15000}")
    @Transactional
    public void processBatch() {
        List<QueueRepository.QueueEntry> entries = queueRepository.lockNextBatch(TOPIC, BATCH_SIZE);
        for (QueueRepository.QueueEntry entry : entries) {
            process(entry);
        }
    }

    void process(QueueRepository.QueueEntry entry) {
        try {
            PricePreloadMessage message = objectMapper.readValue(entry.payload(), PricePreloadMessage.class);
            if (message.instruments() == null) {
                throw new IllegalArgumentException("instruments fehlt");
            }
            for (PricePreloadMessage.Instrument instrument : message.instruments()) {
                if (instrument == null || instrument.symbol() == null || instrument.symbol().isBlank()) {
                    continue;
                }
                priceService.getPrices(instrument.symbol().trim(), instrument.micCode(), MONTHLY_INTERVAL, MONTHS_TO_LOAD);
            }
            queueRepository.delete(entry.id());
        } catch (RuntimeException e) {
            // For example Twelve Data unreachable or quota exhausted: retry later.
            queueRepository.registerFailure(entry.id(), e.getMessage(), MAX_ATTEMPTS);
            log.warn("kursdaten.vorladen: Nachricht {} nicht verarbeitet – {}", entry.id(), e.getMessage());
        }
    }
}
