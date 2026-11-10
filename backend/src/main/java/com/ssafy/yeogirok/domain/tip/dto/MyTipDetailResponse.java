package com.ssafy.yeogirok.domain.tip.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.service.TipPhotoUrls.IssuedPhoto;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-090 내 팁 상세. 현재 저장본을 준다(수정 작업 중·실패여도 마찬가지). 좌표·공간 참조·placement 는 원격으로 주지 않는다.
 * @param photos 0~1개
 */
public record MyTipDetailResponse(
	Long tipId,
	ArTipCategory category,
	String body,
	List<Photo> photos,
	TipStatus status,
	Integer version,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant publishedAt,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant createdAt,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant updatedAt
) {

	public record Photo(
		Long imageId,
		String url,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant expiresAt
	) {
	}

	public static MyTipDetailResponse of(ArTip tip, List<IssuedPhoto> photos) {
		return new MyTipDetailResponse(tip.getId(), tip.getCategory(), tip.getBody(),
			photos.stream().map(photo -> new Photo(photo.imageId(), photo.url(), photo.expiresAt())).toList(),
			tip.getStatus(), tip.getVersion(), tip.getPublishedAt(), tip.getCreatedAt(), tip.getUpdatedAt());
	}
}
