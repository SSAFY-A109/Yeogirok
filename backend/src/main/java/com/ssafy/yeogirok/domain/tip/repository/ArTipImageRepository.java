package com.ssafy.yeogirok.domain.tip.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;

public interface ArTipImageRepository extends JpaRepository<ArTipImage, Long> {

	boolean existsByImagePath(String imagePath);

	boolean existsByImagePathAndTipIdNot(String imagePath, Long tipId);

	Optional<ArTipImage> findByTipId(Long tipId);

	void deleteByTipId(Long tipId);

	/** 주어진 경로 중 팁에 연결된 것. */
	@Query("select i.imagePath from ArTipImage i where i.imagePath in :paths")
	List<String> findLinkedPaths(@Param("paths") Collection<String> paths);
}
