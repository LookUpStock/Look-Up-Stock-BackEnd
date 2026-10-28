package com.lookupstock.backend.dto;

import java.util.List;

public record ErrorBody(String code, String message, List<ErrorDetail> details) {}
