package com.ssafy.yeogirok.domain.tip.entity;

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

/** AR 팁 사진(팁당 최대 1장). 노션 DDL {@code ar_tip_images} 와 맞춘다. DDL 과 다른 점: FK·컬럼 기본값은 Flyway V1 에 맡긴다. */
@Getter
@Entity
@Table(name = "ar_tip_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArTipImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tip_id", nullable = false, unique = true)
	private Long tipId;

	/** 확정 파일 경로(tip-photo/{uploadId}). 서명 URL 은 저장하지 않는다. */
	@Column(name = "image_path", nullable = false, columnDefinition = "text")
	private String imagePath;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
