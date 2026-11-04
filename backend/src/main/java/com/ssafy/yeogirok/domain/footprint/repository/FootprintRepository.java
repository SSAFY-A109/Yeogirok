package com.ssafy.yeogirok.domain.footprint.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;

public interface FootprintRepository extends JpaRepository<Footprint, Long>, JpaSpecificationExecutor<Footprint> {

	Optional<Footprint> findByIdAndDeletedAtIsNull(Long id);
}
