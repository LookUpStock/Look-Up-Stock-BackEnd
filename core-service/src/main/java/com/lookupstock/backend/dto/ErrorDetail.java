package com.lookupstock.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(String field, Long assetId, String name, String message) {

    public ErrorDetail(String field, String message) {
        this(field, null, null, message);
    }
}
