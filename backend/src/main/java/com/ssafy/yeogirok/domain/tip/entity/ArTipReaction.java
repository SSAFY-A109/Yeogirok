package com.ssafy.yeogirok.domain.tip.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AR 팁 반응. 노션 DDL {@code ar_tip_reactions} 와 맞춘다. DDL 과 다른 점: FK·ENUM 타입·컬럼 기본값은 Flyway V1 에 맡긴다. */
@Getter
@Entity
@IdClass(ArTipReactionId.class)
@Table(name = "ar_tip_reactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArTipReaction {

	@Id
	@Column(name = "tip_id")
	private Long tipId;

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipReaction reaction;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
