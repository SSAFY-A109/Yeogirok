package com.ssafy.yeogirok.domain.tip.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;

/** API-047 응답. 다음 페이지가 없어 nextCursor 는 항상 null 이다(specs/ar-tip-api.md 5절 3). */
public record TipSearchResponse(
	List<Item> items,
	String nextCursor,
	int appliedRadiusMeters,
	long totalCount,
	int interactionRadiusMeters
) {

	/** summary 로 보여 줄 본문 앞부분 길이(code point, 11/8 결정). */
	static final int SUMMARY_LENGTH = 40;

	/**
	 * @param distanceMeters 반올림하지 않는다. 반올림하면 canInteract 와 048·052 의 5m 판정이 어긋날 수 있다.
	 */
	public record Item(
		Long id,
		ArTipCategory category,
		BigDecimal latitude,
		BigDecimal longitude,
		String summary,
		SpatialReference spatialReference,
		Map<String, Object> placement,
		double distanceMeters,
		boolean canInteract
	) {

		public static Item of(ArTip tip, double distanceMeters, boolean canInteract) {
			return new Item(tip.getId(), tip.getCategory(), tip.getLatitude(), tip.getLongitude(),
				TipSearchResponse.summary(tip.getBody()), tip.spatialReference(), tip.getPlacement(), distanceMeters,
				canInteract);
		}
	}

	/** 본문 앞 40자, 넘으면 "…"를 붙인다. */
	static String summary(String body) {
		if (body.codePointCount(0, body.length()) <= SUMMARY_LENGTH) {
			return body;
		}
		return body.substring(0, body.offsetByCodePoints(0, SUMMARY_LENGTH)) + "…";
	}
}
