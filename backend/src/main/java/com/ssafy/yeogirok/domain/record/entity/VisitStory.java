package com.ssafy.yeogirok.domain.record.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방문별 이야기. 노션 DDL {@code visit_stories} 와 맞춘다. DDL 과 다른 점: FK(actual_visits·users) 없음.
 * 공개 응답에는 넣지 않는다(이야기는 비공개).
 */
@Getter
@Entity
@Table(name = "visit_stories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitStory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "visit_id", nullable = false)
	private Long visitId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false, columnDefinition = "text")
	private String body;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public static VisitStory write(Long visitId, Long authorId, String body, Instant now) {
		VisitStory story = new VisitStory();
		story.visitId = visitId;
		story.authorId = authorId;
		story.body = body;
		story.createdAt = now;
		story.updatedAt = now;
		return story;
	}

	/** @return 본문이 바뀌었으면 true */
	public boolean edit(String body, Instant now) {
		if (this.body.equals(body)) {
			return false;
		}
		this.body = body;
		this.updatedAt = now;
		return true;
	}
}
