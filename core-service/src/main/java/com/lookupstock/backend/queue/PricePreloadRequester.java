package com.lookupstock.backend.queue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.Position;

@Component
public class PricePreloadRequester {

    private static final Logger log = LoggerFactory.getLogger(PricePreloadRequester.class);

    private final MessagePublisher messagePublisher;

    public PricePreloadRequester(MessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    public void request(Portfolio portfolio) {
        try {
            List<PricePreloadMessage.Instrument> instruments = portfolio.getPositions().stream()
                    .map(Position::getAsset)
                    .map(PricePreloadRequester::toInstrument)
                    .distinct()
                    .toList();
            if (instruments.isEmpty()) {
                return;
            }
            messagePublisher.publish(QueueTopics.PRICE_PRELOAD, new PricePreloadMessage(instruments));
        } catch (RuntimeException e) {
            log.warn("Vorladen der Kurse für Portfolio {} konnte nicht in die Queue gestellt werden: {}",
                    portfolio.getId(), e.getMessage());
        }
    }

    private static PricePreloadMessage.Instrument toInstrument(Asset asset) {
        return new PricePreloadMessage.Instrument(asset.getSymbol(), asset.getMicCode());
    }
}
