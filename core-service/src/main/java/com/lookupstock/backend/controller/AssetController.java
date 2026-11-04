package com.lookupstock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.AssetResponse;
import com.lookupstock.backend.dto.SearchResponse;
import com.lookupstock.backend.service.AssetService;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Integer limit) {
        List<AssetResponse> results = assetService.search(q, limit).stream()
                .map(AssetResponse::from)
                .toList();
        return new SearchResponse(q, results);
    }
}
