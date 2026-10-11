package com.lookupstock.backend.client;

import java.util.List;

record TwelveDataSearchResponse(List<TwelveDataSymbol> data, String status) {}