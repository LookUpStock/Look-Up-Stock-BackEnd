package com.lookupstock.backend.exception;

import java.util.ArrayList;
import java.util.List;

import com.lookupstock.backend.model.Asset;

public class InsufficientDataException extends RuntimeException {

    public record AffectedAsset(Long assetId, String name, String detail) {
    }

    private final List<AffectedAsset> assets;

    public InsufficientDataException(List<Asset> tooShort) {
        this(List.of(), tooShort);
    }

    public InsufficientDataException(List<Asset> noData, List<Asset> tooShort) {
        super(message(noData, tooShort));
        List<AffectedAsset> affected = new ArrayList<>();
        noData.forEach(a -> affected.add(new AffectedAsset(a.getId(), a.getName(), Messages.NO_PRICE_DATA_DETAIL)));
        tooShort.forEach(a -> affected.add(new AffectedAsset(a.getId(), a.getName(), Messages.INSUFFICIENT_DATA_DETAIL)));
        this.assets = List.copyOf(affected);
    }

    private static String message(List<Asset> noData, List<Asset> tooShort) {
        List<String> parts = new ArrayList<>();
        if (!noData.isEmpty()) {
            parts.add(Messages.noPriceData(noData.stream().map(Asset::getName).toList()));
        }
        if (!tooShort.isEmpty()) {
            parts.add(Messages.insufficientData(tooShort.stream().map(Asset::getName).toList()));
        }
        return String.join(" ", parts);
    }

    public List<AffectedAsset> getAssets() {
        return assets;
    }
}
