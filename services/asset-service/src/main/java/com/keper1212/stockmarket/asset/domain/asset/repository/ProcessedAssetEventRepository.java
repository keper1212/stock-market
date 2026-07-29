package com.keper1212.stockmarket.asset.domain.asset.repository;

import com.keper1212.stockmarket.asset.domain.asset.entity.ProcessedAssetEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedAssetEventRepository extends JpaRepository<ProcessedAssetEvent, UUID> {
}
