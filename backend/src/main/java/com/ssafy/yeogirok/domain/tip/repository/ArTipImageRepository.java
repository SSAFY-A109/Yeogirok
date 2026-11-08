package com.ssafy.yeogirok.domain.tip.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;

public interface ArTipImageRepository extends JpaRepository<ArTipImage, Long> {

	boolean existsByImagePath(String imagePath);

	Optional<ArTipImage> findByTipId(Long tipId);
}
