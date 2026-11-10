package com.ssafy.yeogirok.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.push.entity.PushPlatform;
import com.ssafy.yeogirok.domain.push.service.PushTokenService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestAuthConfig;
import com.ssafy.yeogirok.support.TestIdTokens;

import tools.jackson.databind.json.JsonMapper;

/** API-095·096 푸시 토큰 등록·삭제와 탈퇴 시 삭제. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAuthConfig.class)
class PushTokenApiTest {

	private static final String PATH = "/api/v1/users/me/push-tokens";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JsonMapper jsonMapper;
	@Autowired
	TestIdTokens idTokens;
	@Autowired
	MutableClock clock;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PushTokenService pushTokenService;

	@BeforeEach
	void reset() {
		clock.set(TestAuthConfig.START);
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	/** 카카오로 새 회원을 만들고 [액세스 토큰, 회원 ID] 를 돌려준다. */
	private Member signUp() throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\": \"kakao\", \"idToken\": \"%s\", \"nonce\": \"%s\"}".formatted(
					idTokens.kakao(UUID.randomUUID().toString(), clock.instant()), TestIdTokens.NONCE)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String accessToken = jsonMapper.readTree(body).get("data").get("accessToken").asString();
		String me = mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andReturn().getResponse().getContentAsString();
		return new Member(accessToken, jsonMapper.readTree(me).get("data").get("id").asLong());
	}

	private ResultActions register(Member member, String json) throws Exception {
		return mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions register(Member member, String token, String platform) throws Exception {
		return register(member, "{\"token\": \"%s\", \"platform\": \"%s\"}".formatted(token, platform));
	}

	private ResultActions removeRaw(Member member, String json) throws Exception {
		return mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions remove(Member member, String token) throws Exception {
		return removeRaw(member, "{\"token\": \"%s\"}".formatted(token));
	}

	/** 그 토큰의 주인. 없으면 빈 목록. */
	private List<Long> ownersOf(String token) {
		return jdbc.queryForList("SELECT user_id FROM user_push_tokens WHERE token = ?", Long.class, token);
	}

	private static String newToken() {
		return "fcm-" + UUID.randomUUID();
	}

	private static void expectError(ResultActions result, String code) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value(code));
	}

	record Member(String accessToken, long id) {
	}

	/* ── API-095 ──────────────────────────────────────────────── */

	@Test
	void 등록하면_그_회원의_토큰으로_저장한다() throws Exception {
		Member member = signUp();
		String token = newToken();

		register(member, token, "ANDROID")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		assertThat(ownersOf(token)).containsExactly(member.id());
		assertThat(jdbc.queryForObject("SELECT platform FROM user_push_tokens WHERE token = ?", String.class, token))
			.isEqualTo("ANDROID");
	}

	@Test
	void 같은_토큰을_다시_등록해도_성공하고_한_줄만_남는다() throws Exception {
		Member member = signUp();
		String token = newToken();

		register(member, token, "ANDROID").andExpect(status().isOk());
		register(member, token, "ANDROID").andExpect(status().isOk());

		assertThat(ownersOf(token)).containsExactly(member.id());
	}

	@Test
	void 한_회원이_여러_기기_토큰을_둘_수_있다() throws Exception {
		Member member = signUp();
		String phone = newToken();
		String tablet = newToken();

		register(member, phone, "ANDROID").andExpect(status().isOk());
		register(member, tablet, "ANDROID").andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_push_tokens WHERE user_id = ?", Long.class,
			member.id())).isEqualTo(2L);
	}

	@Test
	void 다른_계정이_같은_토큰을_등록하면_지금_계정으로_옮긴다() throws Exception {
		Member before = signUp();
		Member after = signUp();
		String token = newToken();
		register(before, token, "ANDROID").andExpect(status().isOk());

		register(after, token, "ANDROID").andExpect(status().isOk());

		assertThat(ownersOf(token)).containsExactly(after.id());
	}

	@Test
	void 토큰_생략은_C001() throws Exception {
		expectError(register(signUp(), "{\"platform\": \"ANDROID\"}"), "C001");
	}

	@Test
	void 플랫폼_생략은_C001() throws Exception {
		expectError(register(signUp(), "{\"token\": \"%s\"}".formatted(newToken())), "C001");
	}

	@Test
	void 토큰이_문자열이_아니면_C002() throws Exception {
		expectError(register(signUp(), "{\"token\": 123, \"platform\": \"ANDROID\"}"), "C002");
	}

	@Test
	void 빈_토큰이나_공백만_있는_토큰은_C003() throws Exception {
		Member member = signUp();
		expectError(register(member, "", "ANDROID"), "C003");
		expectError(register(member, "   ", "ANDROID"), "C003");
	}

	@Test
	void 지원하지_않는_플랫폼은_C003() throws Exception {
		Member member = signUp();
		expectError(register(member, newToken(), "IOS"), "C003");
		expectError(register(member, newToken(), "android"), "C003");
	}

	@Test
	void 토큰_없이_부르면_A001() throws Exception {
		mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"%s\", \"platform\": \"ANDROID\"}".formatted(newToken())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	/* ── API-096 ──────────────────────────────────────────────── */

	@Test
	void 삭제하면_지우고_다시_삭제해도_성공한다() throws Exception {
		Member member = signUp();
		String token = newToken();
		register(member, token, "ANDROID").andExpect(status().isOk());

		remove(member, token).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
		remove(member, token).andExpect(status().isOk());

		assertThat(ownersOf(token)).isEmpty();
	}

	@Test
	void 다른_계정의_토큰은_지우지_않고_성공만_돌려준다() throws Exception {
		Member owner = signUp();
		Member other = signUp();
		String token = newToken();
		register(owner, token, "ANDROID").andExpect(status().isOk());

		remove(other, token).andExpect(status().isOk());

		assertThat(ownersOf(token)).containsExactly(owner.id());
	}

	@Test
	void 삭제_요청의_빈_토큰은_C003_생략은_C001() throws Exception {
		Member member = signUp();
		expectError(remove(member, " "), "C003");
		expectError(removeRaw(member, "{}"), "C001");
	}

	@Test
	void 같은_토큰의_첫_등록이_동시에_와도_모두_성공하고_한_줄만_남는다() throws Exception {
		Member first = signUp();
		Member second = signUp();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// 충돌이 매번 나지는 않으므로 여러 번 겨룬다.
			for (int round = 0; round < 20; round++) {
				String token = newToken();
				CountDownLatch start = new CountDownLatch(1);
				Future<?> a = pool.submit(() -> registerAfter(start, first, token));
				Future<?> b = pool.submit(() -> registerAfter(start, second, token));
				start.countDown();
				a.get(10, TimeUnit.SECONDS);
				b.get(10, TimeUnit.SECONDS);

				assertThat(ownersOf(token)).hasSize(1).containsAnyOf(first.id(), second.id());
			}
		} finally {
			pool.shutdownNow();
		}
	}

	private Void registerAfter(CountDownLatch start, Member member, String token) throws InterruptedException {
		start.await();
		pushTokenService.register(member.id(), token, PushPlatform.ANDROID);
		return null;
	}

	@Test
	void 탈퇴한_회원은_아직_유효한_액세스_토큰이_있어도_토큰을_등록하지_못한다() throws Exception {
		Member leaving = signUp();
		mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + leaving.accessToken()))
			.andExpect(status().isOk());
		String token = newToken();

		// 탈퇴 커밋과 액세스 토큰 차단 사이에 들어온 등록. JWT 필터를 지나 서비스까지 온 경우를 서비스 호출로 본다.
		assertThatThrownBy(() -> pushTokenService.register(leaving.id(), token, PushPlatform.ANDROID))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.ACCESS_TOKEN_INVALID));
		assertThat(ownersOf(token)).isEmpty();
	}

	/* ── API-009 탈퇴 ─────────────────────────────────────────── */

	@Test
	void 탈퇴하면_그_회원의_토큰을_모두_지운다() throws Exception {
		Member leaving = signUp();
		Member staying = signUp();
		String phone = newToken();
		String tablet = newToken();
		String othersToken = newToken();
		register(leaving, phone, "ANDROID").andExpect(status().isOk());
		register(leaving, tablet, "ANDROID").andExpect(status().isOk());
		register(staying, othersToken, "ANDROID").andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + leaving.accessToken()))
			.andExpect(status().isOk());

		assertThat(ownersOf(phone)).isEmpty();
		assertThat(ownersOf(tablet)).isEmpty();
		assertThat(ownersOf(othersToken)).containsExactly(staying.id());
	}
}
