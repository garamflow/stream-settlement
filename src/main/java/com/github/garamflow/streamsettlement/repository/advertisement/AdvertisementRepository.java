package com.github.garamflow.streamsettlement.repository.advertisement;

import com.github.garamflow.streamsettlement.entity.stream.advertisement.Advertisement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {
}
