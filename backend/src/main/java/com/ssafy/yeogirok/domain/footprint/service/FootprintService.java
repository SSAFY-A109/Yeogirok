package com.ssafy.yeogirok.domain.footprint.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintDeleteResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintListResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.footprint.entity.FootprintBody;
import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.domain.footprint.repository.FootprintSpecifications;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.BoundingBox;
import com.ssafy.yeogirok.global.lock.UserLock;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FootprintService {

	/** API-042 limit 기본값·최댓값. */
	static final int DEFAULT_LIST_LIMIT = 20;
	static final int MAX_LIST_LIMIT = 50;

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final FootprintFinder footprintFinder;
	private final FootprintRepository footprintRepository;
	private final UserLock userLock;

	/** API-043. 본인 것만, 삭제한 것은 F001. */
	@Transactional(readOnly = true)
	public FootprintDetailResponse getFootprint(Long userId, Long footprintId) {
		return FootprintDetailResponse.from(footprintFinder.getOwnFootprint(footprintId, userId));
	}

	/** API-044. 본문만 바꾸고 version 을 올린다. version 이 다르거나 동시 요청이 @Version 에 걸리면 C004. */
	@Transactional
	public FootprintUpdateResponse updateBody(Long userId, Long footprintId, int version, String body) {
		FootprintBody.normalize(body);  // 본문 길이 오류(C003)는 요청값 문제라 조회보다 먼저 알린다
		Footprint footprint = footprintFinder.getOwnFootprint(footprintId, userId);
		if (footprint.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		footprint.updateBody(body);
		// 응답에 올라간 version·updatedAt 을 담으려고 지금 반영한다.
		footprintRepository.flush();
		return FootprintUpdateResponse.from(footprint);
	}

	/** API-045. 사용자 잠금 → 행 잠금 순서로 API-041 작성 제한 검사와 한 줄로 세운다. */
	@Transactional
	public FootprintDeleteResponse delete(Long userId, Long footprintId, int version) {
		userLock.lock(userId);
		Footprint footprint = footprintFinder.getOwnFootprintForUpdate(footprintId, userId);
		if (footprint.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		footprint.softDelete(Instant.now());
		footprintRepository.flush();
		return FootprintDeleteResponse.applied();
	}

	/** API-042. visitedAt 최신순(같으면 id 큰 순). from·to 는 Asia/Seoul 날짜 기준, 양끝 포함. */
	@Transactional(readOnly = true)
	public FootprintListResponse getMyFootprints(Long userId, String bbox, LocalDate from, LocalDate to,
		String category, String cursor, Integer limit) {
		BoundingBox box = BoundingBox.parseOrNull(bbox);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "from 이 to 보다 늦습니다.");
		}
		if (category != null && TravelCategory.fromCode(category).isEmpty()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "category: 없는 카테고리입니다: " + category);
		}
		FootprintCursor after = FootprintCursor.decodeOrNull(cursor);
		int size = limit == null ? DEFAULT_LIST_LIMIT : limit;
		if (size < 1 || size > MAX_LIST_LIMIT) {
			throw new BusinessException(ExceptionType.PAGE_SIZE_OUT_OF_RANGE,
				"limit: 1~%d 사이여야 합니다.".formatted(MAX_LIST_LIMIT));
		}

		Specification<Footprint> spec = FootprintSpecifications.ownedAndNotDeleted(userId);
		if (box != null) {
			spec = spec.and(FootprintSpecifications.within(box));
		}
		if (from != null || to != null) {
			spec = spec.and(FootprintSpecifications.visitedBetween(
				from == null ? null : from.atStartOfDay(SEOUL).toInstant(),
				to == null ? null : to.plusDays(1).atStartOfDay(SEOUL).toInstant()));
		}
		if (category != null) {
			spec = spec.and(FootprintSpecifications.hasCategory(category));
		}
		if (after != null) {
			spec = spec.and(FootprintSpecifications.after(after.visitedAt(), after.id()));
		}

		// 한 개 더 읽어서 다음 쪽이 있는지 본다.
		List<Footprint> rows = footprintRepository.findBy(spec, query -> query
			.sortBy(Sort.by(Sort.Order.desc("visitedAt"), Sort.Order.desc("id")))
			.limit(size + 1)
			.all());
		boolean hasNext = rows.size() > size;
		List<Footprint> page = hasNext ? rows.subList(0, size) : rows;
		String nextCursor = null;
		if (hasNext) {
			Footprint last = page.getLast();
			nextCursor = new FootprintCursor(last.getVisitedAt(), last.getId()).encode();
		}
		return new FootprintListResponse(page.stream().map(FootprintListResponse.Item::from).toList(), nextCursor);
	}
}
