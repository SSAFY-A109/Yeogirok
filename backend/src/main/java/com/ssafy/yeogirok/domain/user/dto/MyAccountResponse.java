package com.ssafy.yeogirok.domain.user.dto;

import java.util.List;

/** API-008 응답 data. */
public record MyAccountResponse(
	Long id,
	String nickname,
	String profileImageUrl,
	ActivityCounts activityCounts,
	List<String> loginProviders
) {

	/** 발자취·팁 도메인이 생기기 전까지는 0 이다. */
	public record ActivityCounts(
		long footprints,
		long tips
	) {
	}
}
