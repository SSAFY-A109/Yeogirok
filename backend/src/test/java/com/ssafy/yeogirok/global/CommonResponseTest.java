package com.ssafy.yeogirok.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.exception.GlobalExceptionHandler;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공통 응답 봉투와 GlobalExceptionHandler 의 예외 → 코드 매핑.
 *
 * <p>스프링 컨텍스트 없이 standalone MockMvc 로 돌린다. 시큐리티 필터·DB 와 상관없이 응답 모양만 본다.
 */
class CommonResponseTest {

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** 실패 봉투는 success·code·message 세 필드만 갖는다. */
	private static void expectFailure(ResultActions result, ExceptionType type) throws Exception {
		result.andExpect(status().is(type.getStatus().value()))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value(type.getCode()))
			.andExpect(jsonPath("$.message").isString())
			.andExpect(jsonPath("$.length()").value(3));
	}

	/* ── 성공 ─────────────────────────────────────────────────── */

	@Nested
	class 성공_응답 {

		@Test
		void data_를_감싸서_보낸다() throws Exception {
			mockMvc.perform(get("/test/data"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{ "success": true, "data": { "name": "여기록" } }
					""", JsonCompareMode.STRICT));
		}

		@Test
		void 빈_성공은_data_를_생략한다() throws Exception {
			mockMvc.perform(get("/test/empty"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{ "success": true }
					""", JsonCompareMode.STRICT));
		}
	}

	/* ── BusinessException ────────────────────────────────────── */

	@Nested
	class 비즈니스_예외 {

		@Test
		void 코드와_기본_메시지로_응답한다() throws Exception {
			mockMvc.perform(get("/test/business"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
					{ "success": false, "code": "C003", "message": "허용하지 않는 요청값입니다." }
					""", JsonCompareMode.STRICT));
		}

		@Test
		void 덮어쓴_메시지로_응답한다() throws Exception {
			mockMvc.perform(get("/test/business").param("message", "provider 가 올바르지 않습니다."))
				.andExpect(jsonPath("$.code").value("C003"))
				.andExpect(jsonPath("$.message").value("provider 가 올바르지 않습니다."));
		}
	}

	/* ── 요청값 검증 ──────────────────────────────────────────── */

	@Nested
	class 요청값_검증 {

		@Test
		void 본문_필수값이_비면_C001() throws Exception {
			expectFailure(postJson("/test/body", "{\"name\": \"\"}"), ExceptionType.REQUIRED_VALUE_MISSING);
		}

		@Test
		void 본문_필수값이_없으면_C001() throws Exception {
			expectFailure(postJson("/test/body", "{}"), ExceptionType.REQUIRED_VALUE_MISSING);
		}

		@Test
		void 본문_값이_제약을_어기면_C003() throws Exception {
			expectFailure(postJson("/test/body", "{\"name\": \"열한글자가넘는이름입니다\"}"),
				ExceptionType.NOT_ALLOWED_VALUE);
		}

		@Test
		void 파라미터가_제약을_어기면_C003() throws Exception {
			expectFailure(mockMvc.perform(get("/test/param").param("page", "0")), ExceptionType.NOT_ALLOWED_VALUE);
		}

		@Test
		void 필수_파라미터가_없으면_C001() throws Exception {
			expectFailure(mockMvc.perform(get("/test/param")), ExceptionType.REQUIRED_VALUE_MISSING);
		}

		@Test
		void 필수_헤더가_없으면_C001() throws Exception {
			expectFailure(mockMvc.perform(get("/test/header")), ExceptionType.REQUIRED_VALUE_MISSING);
		}

		@Test
		void 파라미터_타입이_다르면_C002() throws Exception {
			expectFailure(mockMvc.perform(get("/test/param").param("page", "abc")),
				ExceptionType.INVALID_VALUE_FORMAT);
		}

		@Test
		void 본문이_JSON_이_아니면_C002() throws Exception {
			expectFailure(postJson("/test/body", "{name:"), ExceptionType.INVALID_VALUE_FORMAT);
		}
	}

	/* ── 그 외 ────────────────────────────────────────────────── */

	@Nested
	class 그_외_예외 {

		@Test
		void 메서드_보안_거부는_A004() throws Exception {
			expectFailure(mockMvc.perform(get("/test/denied")), ExceptionType.ROLE_INSUFFICIENT);
		}

		@Test
		void 허용되지_않은_메서드() throws Exception {
			expectFailure(mockMvc.perform(get("/test/body")), ExceptionType.HTTP_METHOD_NOT_ALLOWED);
		}

		@Test
		void 지원하지_않는_Content_Type() throws Exception {
			expectFailure(mockMvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("name")),
				ExceptionType.MEDIA_TYPE_NOT_SUPPORTED);
		}

		@Test
		void 예상하지_못한_예외는_원인을_숨기고_고정_문구로_응답한다() throws Exception {
			mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().json("""
					{ "success": false, "code": "C014", "message": "일시적인 오류입니다. 잠시 후 다시 시도해 주세요." }
					""", JsonCompareMode.STRICT));
		}
	}

	/* ── 테스트용 컨트롤러 ────────────────────────────────────── */

	record NameRequest(@NotBlank @Size(max = 10) String name) {
	}

	/**
	 * 일부러 static 이 아닌 내부 클래스로 둔다. 컴포넌트 스캔은 독립적으로 만들 수 없는 내부 클래스를 건너뛰므로,
	 * IDE 가 테스트 클래스를 클래스패스에 넣고 앱을 띄워도 이 컨트롤러가 등록되지 않는다.
	 */
	@RestController
	class TestController {

		@GetMapping("/test/data")
		ApiResponseBody<Map<String, String>> data() {
			return ApiResponseUtil.createSuccessResponse(Map.of("name", "여기록"));
		}

		@GetMapping("/test/empty")
		ApiResponseBody<Void> empty() {
			return ApiResponseUtil.createSuccessResponse();
		}

		@GetMapping("/test/business")
		ApiResponseBody<Void> business(@RequestParam(required = false) String message) {
			throw message == null
				? new BusinessException(ExceptionType.NOT_ALLOWED_VALUE)
				: new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
		}

		@PostMapping("/test/body")
		ApiResponseBody<Void> body(@Valid @RequestBody NameRequest request) {
			return ApiResponseUtil.createSuccessResponse();
		}

		@GetMapping("/test/param")
		ApiResponseBody<Void> param(@RequestParam @Min(1) int page) {
			return ApiResponseUtil.createSuccessResponse();
		}

		@GetMapping("/test/header")
		ApiResponseBody<Void> header(@RequestHeader("X-Device-Id") String deviceId) {
			return ApiResponseUtil.createSuccessResponse();
		}

		@GetMapping("/test/denied")
		ApiResponseBody<Void> denied() {
			throw new AuthorizationDeniedException("denied");
		}

		@GetMapping("/test/unexpected")
		ApiResponseBody<Void> unexpected() {
			throw new IllegalStateException("SELECT * FROM users 실패");
		}
	}
}
