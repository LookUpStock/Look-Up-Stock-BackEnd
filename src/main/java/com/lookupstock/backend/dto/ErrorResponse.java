package com.lookupstock.backend.dto;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(String code, String message, List<ErrorDetail> details) {
        return new ErrorResponse(new ErrorBody(code, message, details));
    }
}