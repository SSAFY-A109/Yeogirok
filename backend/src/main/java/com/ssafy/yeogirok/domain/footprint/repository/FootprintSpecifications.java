package com.ssafy.yeogirok.domain.footprint.repository;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.geo.BoundingBox;

/** API-042 목록 조건. 값이 없는 조건은 쓰지 않는다. */
public final class FootprintSpecifications {

	private FootprintSpecifications() {
	}

	public static Specification<Footprint> ownedAndNotDeleted(Long userId) {
		return (root, query, cb) -> cb.and(
			cb.equal(root.get("userId"), userId),
			cb.isNull(root.get("deletedAt")));
	}

	public static Specification<Footprint> within(BoundingBox box) {
		return (root, query, cb) -> cb.and(
			cb.between(root.get("longitude"), box.west(), box.east()),
			cb.between(root.get("latitude"), box.south(), box.north()));
	}

	/** visitedAt 이 [fromInclusive, toExclusive) 안. 둘 중 없는 쪽은 열어 둔다. */
	public static Specification<Footprint> visitedBetween(Instant fromInclusive, Instant toExclusive) {
		return (root, query, cb) -> cb.and(
			fromInclusive == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("visitedAt"), fromInclusive),
			toExclusive == null ? cb.conjunction() : cb.lessThan(root.get("visitedAt"), toExclusive));
	}

	/**
	 * 카테고리 코드를 포함. JSON 배열을 문자열로 바꿔 {@code "CODE"} 를 찾는다.
	 * 코드가 고정된 영문 대문자라 따옴표까지 맞추면 다른 코드와 섞이지 않고, PostgreSQL·H2 에서 똑같이 동작한다.
	 */
	public static Specification<Footprint> hasCategory(String categoryCode) {
		// as() 는 SQL CAST 를 만들지 않아 PostgreSQL 에서 jsonb LIKE 가 된다. cast() 를 써야 한다.
		return (root, query, cb) -> cb.like(root.get("categoryCodes").cast(String.class), "%\"" + categoryCode + "\"%");
	}

	/** visitedAt 최신순·id 큰 순에서 커서 다음 항목. */
	public static Specification<Footprint> after(Instant visitedAt, Long id) {
		return (root, query, cb) -> cb.or(
			cb.lessThan(root.get("visitedAt"), visitedAt),
			cb.and(cb.equal(root.get("visitedAt"), visitedAt), cb.lessThan(root.get("id"), id)));
	}
}
