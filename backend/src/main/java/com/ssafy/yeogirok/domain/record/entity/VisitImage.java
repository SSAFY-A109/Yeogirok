package com.ssafy.yeogirok.domain.record.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기록 사진(사진 보관함의 영구 데이터). 노션 DDL {@code visit_images} 와 맞춘다. DDL 과 다른 점: FK(travel_records·
 * actual_visits·users) 없음, {@code UNIQUE (visit_id, position) DEFERRABLE} 은 JPA 로 만들 수 없어 Flyway V1 에 맡긴다.
 * image_path 는 고정 비공개 경로이고 서명 URL 을 저장하지 않는다.
 */
@Getter
@Entity
@Table(name = "visit_images",
	uniqueConstraints = @UniqueConstraint(name = "visit_images_record_id_image_path_key",
		columnNames = {"record_id", "image_path"}),
	indexes = @Index(name = "ix_visit_images_record_pool", columnList = "record_id, visit_id, id"),
	check = @CheckConstraint(name = "visit_images_link_check",
		constraint = "(visit_id IS NULL AND position IS NULL AND is_public_selected = FALSE)"
			+ " OR (visit_id IS NOT NULL AND position IS NOT NULL AND position >= 1)"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "record_id", nullable = false)
	private Long recordId;

	/** null 이면 아직 방문에 연결하지 않은 사진. */
	@Column(name = "visit_id")
	private Long visitId;

	@Column(name = "image_path", nullable = false, columnDefinition = "text")
	private String imagePath;

	@Column(name = "uploaded_by", nullable = false)
	private Long uploadedBy;

	/** 연결된 방문 안의 순서. 미연결이면 null. */
	private Integer position;

	@Column(name = "is_public_selected", nullable = false)
	private boolean publicSelected;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** API-086. 업로드를 마친 사진은 방문에 연결하지 않은 채로 보관함에 들어간다. */
	public static VisitImage unlinked(Long recordId, Long uploadedBy, String imagePath, Instant now) {
		VisitImage image = new VisitImage();
		image.recordId = recordId;
		image.uploadedBy = uploadedBy;
		image.imagePath = imagePath;
		image.createdAt = now;
		return image;
	}
}
