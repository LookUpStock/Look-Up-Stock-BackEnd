package com.lookupstock.backend.exception;

import java.util.List;

import com.lookupstock.backend.model.Asset;

/**
 * Für mindestens eine Anlage liegen weniger als 36 Monatswerte vor (E8).
 * Enthält alle betroffenen Anlagen (E6); wird als 422 INSUFFICIENT_DATA beantwortet.
 */
public class InsufficientDataException extends RuntimeException {

    public record AffectedAsset(Long assetId, String name) {
    }

    private final List<AffectedAsset> assets;

    public InsufficientDataException(List<Asset> assets) {
        super(Messages.insufficientData(assets.stream().map(Asset::getName).toList()));
        this.assets = assets.stream().map(a -> new AffectedAsset(a.getId(), a.getName())).toList();
    }

    public List<AffectedAsset> getAssets() {
        return assets;
    }
}
