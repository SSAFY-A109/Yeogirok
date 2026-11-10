package com.ssafy.yeogirok.domain.record.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.record.entity.VisitStory;

public interface VisitStoryRepository extends JpaRepository<VisitStory, Long> {

	/** 방문별 작성 순서(id). */
	List<VisitStory> findByVisitIdInOrderByIdAsc(Collection<Long> visitIds);

	boolean existsByVisitIdIn(Collection<Long> visitIds);

	/** API-067 확인 삭제. CASCADE 가 없어 방문보다 먼저 지운다. */
	void deleteByVisitIdIn(Collection<Long> visitIds);
}
