package com.ssafy.yeogirok.domain.tip.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;

public interface ArTipImageRepository extends JpaRepository<ArTipImage, Long> {

	boolean existsByImagePath(String imagePath);
}
