package com.lookupstock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lookupstock.backend.model.PriceHistory;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByAssetIdOrderByPriceDateAsc(Long assetId);

    @Modifying
    @Query("delete from PriceHistory p where p.asset.id = :assetId")
    void deleteByAssetId(@Param("assetId") Long assetId);
}
