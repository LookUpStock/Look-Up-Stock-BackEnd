package com.lookupstock.marktdaten.twelvedata;

import java.util.List;

record TwelveDataSearchResponse(List<TwelveDataSymbol> data, String status, Integer code, String message) {}
