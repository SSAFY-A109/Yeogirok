package com.ssafy.yeogirok.domain.tip.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-048 응답. photos 는 0~1개이고 url 은 이 요청에서만 발급한 Presigned GET 이다. */
public record TipViewResponse(
	Long id,
	ArTipCategory category,
	String body,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant createdAt,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant publishedAt,
	List<Photo> photos,
	Author author,
	Location location,
	SpatialReference spatialReference,
	Map<String, Object> placement,
	Reactions reactions
) {

	/** @param attribution 사용자가 올린 사진이라 외부 출처 표시가 없어 항상 null */
	public record Photo(Long imageId, String url, String attribution) {
	}

	/** 탈퇴했거나 없는 회원이면 id null, nickname 은 {@link User#WITHDRAWN_NICKNAME}(노션 048). */
	public record Author(Long id, String nickname) {

		public static Author withdrawn() {
			return new Author(null, User.WITHDRAWN_NICKNAME);
		}
	}

	public record Location(BigDecimal latitude, BigDecimal longitude) {
	}

	/** @param myReaction 요청한 사용자의 반응. 없으면 null */
	public record Reactions(long likes, long dislikes, TipReaction myReaction) {
	}
}
