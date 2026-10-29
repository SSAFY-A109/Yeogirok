package com.ssafy.yeogirok.global.response;

import lombok.Getter;

/**
 * 공통 응답 봉투. 성공·실패 두 모양 외로 늘어나지 않도록 닫아 둔다.
 *
 * <pre>
 * { "success": true,  "data": { … } }      빈 성공은 data 를 생략한다
 * { "success": false, "code": "C001", "message": "…" }
 * </pre>
 *
 * <p>이름이 {@code ResponseBody} 가 아닌 이유: 스프링의 {@code @ResponseBody} 와 import 가 겹친다.
 */
@Getter
public sealed abstract class ApiResponseBody<T> permits SuccessResponseBody, FailedResponseBody {
	private final boolean success;

	protected ApiResponseBody(boolean success) {
		this.success = success;
	}
}
