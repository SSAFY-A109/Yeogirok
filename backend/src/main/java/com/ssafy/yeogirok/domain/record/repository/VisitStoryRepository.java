package com.ssafy.yeogirok.domain.record.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.record.entity.VisitStory;

public interface VisitStoryRepository extends JpaRepository<VisitStory, Long> {

	/** 방문별 작성 순서(id). */
	List<VisitStory> findByVisitIdInOrderByIdAsc(Collection<Long> visitIds);
}
