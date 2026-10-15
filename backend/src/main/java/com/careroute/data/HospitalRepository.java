package com.careroute.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HospitalRepository extends JpaRepository<HospitalEntity, String> {
    List<HospitalEntity> findByActiveTrueAndProvinceOrderByNameAsc(String province);
}
