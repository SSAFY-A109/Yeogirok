package com.ssafy.yeogirok.domain.footprint.service;

import java.time.Clock;
import java.time.LocalDate;
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
import com.ssafy.yeogirok.global.request.DateRange;
import com.ssafy.yeogirok.global.request.PageSize;
import com.ssafy.yeogirok.global.request.TimeIdCursor;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FootprintService {

	private final FootprintFinder footprintFinder;
	private final FootprintRepository footprintRepository;
	private final UserLock userLock;
	private final Clock clock;

	/** API-043. 본인 것만, 삭제한 것은 F001. */
	@Transactional(readOnly = true)
	public FootprintDetailResponse getFootprint(Long userId, Long footprintId) {
		return FootprintDetailResponse.from(footprintFinder.getOwnFootprint(footprintId, userId));
	}

	/** API-044. 본문만 바꾸고 version 을 올린다. version 이 다르거나 동시 요청이 @Version 에 걸리면 C004. */
	@Transactional
	public FootprintUpdateResponse updateBody(Long userId, Long footprintId, int version, String body) {
		String normalized = FootprintBody.normalize(body);  // 본문 오류(C003)는 요청값 문제라 조회보다 먼저 알린다
		Footprint footprint = footprintFinder.getOwnFootprint(footprintId, userId);
		if (footprint.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		footprint.updateBody(normalized, clock.instant());
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
		footprint.softDelete(clock.instant());
		footprintRepository.flush();
		return FootprintDeleteResponse.applied();
	}

	/** API-042. visitedAt 최신순(같으면 id 큰 순). from·to 는 Asia/Seoul 날짜 기준, 양끝 포함. */
	@Transactional(readOnly = true)
	public FootprintListResponse getMyFootprints(Long userId, String bbox, LocalDate from, LocalDate to,
		String category, String cursor, Integer limit) {
		BoundingBox box = BoundingBox.parseOrNull(bbox);
		DateRange dates = new DateRange(from, to);
		TravelCategory.validateFilter(category);
		TimeIdCursor after = TimeIdCursor.decodeOrNull(cursor);
		int size = PageSize.resolve(limit);

		Specification<Footprint> spec = FootprintSpecifications.ownedAndNotDeleted(userId);
		if (box != null) {
			spec = spec.and(FootprintSpecifications.within(box));
		}
		if (from != null || to != null) {
			spec = spec.and(FootprintSpecifications.visitedBetween(dates.startInclusive(), dates.endExclusive()));
		}
		if (category != null) {
			spec = spec.and(FootprintSpecifications.hasCategory(category));
		}
		if (after != null) {
			spec = spec.and(FootprintSpecifications.after(after.time(), after.id()));
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
			nextCursor = new TimeIdCursor(last.getVisitedAt(), last.getId()).encode();
		}
		return new FootprintListResponse(page.stream().map(FootprintListResponse.Item::from).toList(), nextCursor);
	}
}
