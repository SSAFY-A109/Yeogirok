package com.ssafy.yeogirok.domain.tip.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchRequest.TipSearchQuery;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.Coordinates;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;

import lombok.RequiredArgsConstructor;

/** API-047 주변 공개 팁 탐색. 반경 안 공개 팁을 가까운 순(같으면 id 순)으로 최대 {@code maxSearchResults}개. */
@Service
@RequiredArgsConstructor
public class TipSearchService {

	/** 위도 1도의 거리(m). 후보 사각형을 넉넉히 잡으려는 근삿값이라 거리 판정에는 쓰지 않는다. */
	private static final double METERS_PER_DEGREE = 111_000;

	private final ArTipRepository arTipRepository;
	private final LocationEvidencePolicy locationEvidencePolicy;
	private final TipProperties properties;
	private final Clock clock;

	@Transactional(readOnly = true)
	public TipSearchResponse search(TipSearchQuery query) {
		LocationEvidence location = query.location();
		locationEvidencePolicy.verify(location, clock.instant());
		int radius = properties.searchRadiusMeters();
		if (query.radiusMeters() != null && query.radiusMeters() != radius) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "radiusMeters: %d만 허용합니다.".formatted(radius));
		}
		int limit = resolveLimit(query.limit());

		List<Candidate> inRadius = candidates(location, radius, query).stream()
			.map(tip -> new Candidate(tip, tip.distanceMetersFrom(location.latitude(), location.longitude())))
			.filter(candidate -> properties.inSearchRadius(candidate.distance()))
			.sorted(Comparator.comparingDouble(Candidate::distance).thenComparing(candidate -> candidate.tip().getId()))
			.toList();
		List<TipSearchResponse.Item> items = inRadius.stream()
			.limit(limit)
			.map(c -> TipSearchResponse.Item.of(c.tip(), c.distance(), properties.canInteract(c.distance())))
			.toList();
		return new TipSearchResponse(items, null, radius, inRadius.size(), properties.interactionRadiusMeters());
	}

	private int resolveLimit(Long limit) {
		int max = properties.maxSearchResults();
		if (limit == null) {
			return max;
		}
		if (limit < 1 || limit > max) {
			throw new BusinessException(ExceptionType.PAGE_SIZE_OUT_OF_RANGE, "limit: 1~%d 사이여야 합니다.".formatted(max));
		}
		return limit.intValue();
	}

	/** 반경을 감싸는 위경도 사각형(1m 여유) 안의 공개 팁. */
	private List<ArTip> candidates(LocationEvidence location, int radius, TipSearchQuery query) {
		double latitude = location.latitude().doubleValue();
		double latDelta = (radius + 1) / METERS_PER_DEGREE;
		double lngDelta = Math.min(180, latDelta / Math.cos(Math.toRadians(latitude)));
		return arTipRepository.findPublicInBox(
			degrees(latitude - latDelta), degrees(latitude + latDelta),
			degrees(location.longitude().doubleValue() - lngDelta), degrees(location.longitude().doubleValue() + lngDelta),
			query.category());
	}

	private static BigDecimal degrees(double value) {
		return BigDecimal.valueOf(value).setScale(Coordinates.SCALE, RoundingMode.HALF_UP);
	}

	private record Candidate(ArTip tip, double distance) {
	}
}
