package com.ssafy.yeogirok.domain.tip.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-053 내 팁 목록. 다음 쪽이 없으면 nextCursor 는 null. 본문·사진·공간 정보는 주지 않는다. */
public record MyTipListResponse(List<Item> items, String nextCursor) {

	public record Item(
		Long id,
		ArTipCategory category,
		TipStatus status,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant createdAt,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant updatedAt
	) {

		public static Item from(ArTip tip) {
			return new Item(tip.getId(), tip.getCategory(), tip.getStatus(), tip.getCreatedAt(), tip.getUpdatedAt());
		}
	}
}
