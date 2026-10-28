package com.lookupstock.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Ein Eintrag in error.details. assetId und name sind nur bei 422 INSUFFICIENT_DATA gesetzt
 * (API-Design, E6) und werden sonst nicht ausgegeben.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(String field, Long assetId, String name, String message) {

    public ErrorDetail(String field, String message) {
        this(field, null, null, message);
    }
}
