package com.lookupstock.marktdaten.twelvedata;

// Twelve Data usually reports errors as HTTP 200 with {"status":"error","code":...}.
enum TwelveDataError {

    INVALID_KEY,
    PLAN_RESTRICTED,
    RATE_LIMIT,
    NOT_FOUND,
    OTHER;

    static TwelveDataError fromCode(Integer code) {
        if (code == null) {
            return OTHER;
        }
        return switch (code) {
            case 401 -> INVALID_KEY;
            case 403 -> PLAN_RESTRICTED;
            case 429 -> RATE_LIMIT;
            case 400, 404 -> NOT_FOUND;
            default -> OTHER;
        };
    }
}
