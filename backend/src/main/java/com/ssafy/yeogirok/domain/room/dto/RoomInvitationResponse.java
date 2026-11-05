package com.ssafy.yeogirok.domain.room.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-024 응답. 초대 링크는 방당 하나라 invitationId 는 방 ID 와 같다(별도 초대 테이블이 없다).
 *
 * @param expiresAt 시간 만료가 없어 항상 null
 * @param created   이번 요청에서 처음 만들었으면 true(201), 기존 링크면 false(200). 응답 본문에는 넣지 않는다
 */
public record RoomInvitationResponse(
	Long invitationId,
	String inviteToken,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant expiresAt,
	String inviteUrl,
	@JsonIgnore boolean created
) {
}
