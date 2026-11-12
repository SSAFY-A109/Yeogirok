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

	/** 발자취는 삭제하지 않은 것, 팁은 내 팁 목록(API-053)에 나오는 것의 수. */
	public record ActivityCounts(
		long footprints,
		long tips
	) {
	}
}
