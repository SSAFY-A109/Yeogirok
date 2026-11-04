package com.ssafy.yeogirok.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.user.unlink.ProviderUnlinkService;
import com.ssafy.yeogirok.domain.user.unlink.ProviderUnlinkTaskStore;
import com.ssafy.yeogirok.support.FakeGoogleOAuthClient;
import com.ssafy.yeogirok.support.FakeKakaoUnlinkClient;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestAuthConfig;
import com.ssafy.yeogirok.support.TestIdTokens;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAuthConfig.class)
class UserApiTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JsonMapper jsonMapper;
	@Autowired
	TestIdTokens idTokens;
	@Autowired
	MutableClock clock;
	@Autowired
	FakeKakaoUnlinkClient kakaoUnlinkClient;
	@Autowired
	FakeGoogleOAuthClient googleOAuthClient;
	@Autowired
	ProviderUnlinkService providerUnlinkService;
	@Autowired
	ProviderUnlinkTaskStore unlinkTaskStore;

	@BeforeEach
	void reset() {
		clock.set(TestAuthConfig.START);
		kakaoUnlinkClient.reset();
		googleOAuthClient.reset();
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	private JsonNode login(String provider, String idToken) throws Exception {
		ResultActions result = mockMvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\": \"%s\", \"idToken\": \"%s\", \"nonce\": \"%s\"}".formatted(provider, idToken,
					TestIdTokens.NONCE)))
			.andExpect(status().isOk());
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
	}

	private ResultActions getMe(String accessToken) throws Exception {
		var request = get("/api/v1/users/me");
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		return mockMvc.perform(request);
	}

	private ResultActions withdraw(String accessToken) throws Exception {
		return withdraw(accessToken, null);
	}

	private ResultActions withdraw(String accessToken, String googleAuthCode) throws Exception {
		var request = delete("/api/v1/users/me");
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		if (googleAuthCode != null) {
			request.contentType(MediaType.APPLICATION_JSON).content("{\"googleAuthCode\": \"%s\"}".formatted(googleAuthCode));
		}
		return mockMvc.perform(request);
	}

	/** 구글 회원 탈퇴용 serverAuthCode 를 등록한다. 교환하면 그 sub 의 ID 토큰이 나온다. */
	private String googleAuthCodeFor(String sub) {
		String code = "code-" + uniqueSub();
		googleOAuthClient.register(code, idTokens.google(sub, clock.instant()));
		return code;
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/token/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)));
	}

	private static String uniqueSub() {
		return UUID.randomUUID().toString();
	}

	/* ── API-008 ──────────────────────────────────────────────── */

	@Nested
	class 내_계정_정보_조회 {

		@Test
		void 로그인한_회원의_정보를_돌려준다() throws Exception {
			String sub = uniqueSub();
			JsonNode tokens = login("google", idTokens.google(sub, clock.instant()));

			getMe(tokens.get("accessToken").asString())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.nickname").value("닉네임-" + sub))
				.andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/p.png"))
				.andExpect(jsonPath("$.data.activityCounts.footprints").value(0))
				.andExpect(jsonPath("$.data.activityCounts.tips").value(0))
				.andExpect(jsonPath("$.data.loginProviders[0]").value("google"))
				.andExpect(jsonPath("$.data.loginProviders.length()").value(1));
		}

		@Test
		void 카카오_회원은_loginProviders_가_kakao() throws Exception {
			JsonNode tokens = login("kakao", idTokens.kakao(uniqueSub(), clock.instant()));

			getMe(tokens.get("accessToken").asString())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.loginProviders[0]").value("kakao"));
		}

		@Test
		void 액세스_토큰이_없으면_A001() throws Exception {
			getMe(null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("A001"));
		}
	}

	/* ── API-009 ──────────────────────────────────────────────── */

	@Nested
	class 회원_탈퇴 {

		@Test
		void 탈퇴하면_withdrawn_과_QUEUED_를_돌려주고_카카오_연결을_끊는다() throws Exception {
			String sub = uniqueSub();
			JsonNode tokens = login("kakao", idTokens.kakao(sub, clock.instant()));

			withdraw(tokens.get("accessToken").asString())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.withdrawn").value(true))
				.andExpect(jsonPath("$.data.dataCleanupStatus").value("QUEUED"));

			assertThat(kakaoUnlinkClient.unlinked()).containsExactly(sub);
		}

		@Test
		void 탈퇴_후_남은_액세스_토큰은_회원을_조회하지_않는_API_에서도_A003() throws Exception {
			JsonNode tokens = login("kakao", idTokens.kakao(uniqueSub(), clock.instant()));
			String accessToken = tokens.get("accessToken").asString();
			withdraw(accessToken).andExpect(status().isOk());

			// 로그아웃(API-007)은 회원을 조회하지 않는다. JWT 검증 단계에서 막혀야 한다.
			mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"refreshToken\": \"%s\"}".formatted(tokens.get("refreshToken").asString())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A003"));
		}

		@Test
		void 같은_회원의_탈퇴가_동시에_와도_한_번만_탈퇴하고_연결도_한_번만_끊는다() throws Exception {
			String sub = uniqueSub();
			String accessToken = login("kakao", idTokens.kakao(sub, clock.instant())).get("accessToken").asString();
			int requests = 3;
			ExecutorService executor = Executors.newFixedThreadPool(requests);
			CountDownLatch start = new CountDownLatch(1);
			try {
				List<Future<Integer>> results = new ArrayList<>();
				for (int i = 0; i < requests; i++) {
					results.add(executor.submit(() -> {
						start.await();
						return withdraw(accessToken).andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();

				List<Integer> statuses = new ArrayList<>();
				for (Future<Integer> result : results) {
					statuses.add(result.get());
				}
				assertThat(statuses).containsOnlyOnce(200);
				assertThat(kakaoUnlinkClient.unlinked()).containsExactly(sub);
			} finally {
				executor.shutdownNow();
			}
		}

		@Test
		void 탈퇴하면_모든_기기의_리프레시_토큰이_A009() throws Exception {
			String sub = uniqueSub();
			JsonNode phone = login("kakao", idTokens.kakao(sub, clock.instant()));
			JsonNode tablet = login("kakao", idTokens.kakao(sub, clock.instant()));

			withdraw(phone.get("accessToken").asString()).andExpect(status().isOk());

			refresh(phone.get("refreshToken").asString()).andExpect(jsonPath("$.code").value("A009"));
			refresh(tablet.get("refreshToken").asString()).andExpect(jsonPath("$.code").value("A009"));
		}

		@Test
		void 탈퇴_후_남은_액세스_토큰으로_내_정보를_조회하면_A003() throws Exception {
			String accessToken = login("kakao", idTokens.kakao(uniqueSub(), clock.instant()))
				.get("accessToken").asString();

			withdraw(accessToken).andExpect(status().isOk());

			getMe(accessToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A003"));
			withdraw(accessToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A003"));
		}

		@Test
		void 같은_계정으로_다시_로그인하면_새_회원으로_가입된다() throws Exception {
			String sub = uniqueSub();
			JsonNode before = login("kakao", idTokens.kakao(sub, clock.instant()));
			long beforeId = data(getMe(before.get("accessToken").asString())).get("id").asLong();

			withdraw(before.get("accessToken").asString()).andExpect(status().isOk());
			JsonNode after = login("kakao", idTokens.kakao(sub, clock.instant()));

			assertThat(after.get("isNewUser").asBoolean()).isTrue();
			assertThat(data(getMe(after.get("accessToken").asString())).get("id").asLong()).isNotEqualTo(beforeId);
		}

		/* 최근 재인증(A011) */

		@Test
		void 재발급받은_액세스_토큰으로는_탈퇴할_수_없다_A011() throws Exception {
			JsonNode tokens = login("kakao", idTokens.kakao(uniqueSub(), clock.instant()));
			JsonNode refreshed = data(refresh(tokens.get("refreshToken").asString()).andExpect(status().isOk()));

			withdraw(refreshed.get("accessToken").asString())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A011"));
		}

		@Test
		void 로그인하고_5분이_지나면_A011_이고_다시_로그인하면_탈퇴된다() throws Exception {
			String sub = uniqueSub();
			JsonNode first = login("kakao", idTokens.kakao(sub, clock.instant()));
			clock.advance(Duration.ofMinutes(5).plusSeconds(1));

			withdraw(first.get("accessToken").asString())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A011"));
			getMe(first.get("accessToken").asString()).andExpect(status().isOk());

			JsonNode again = login("kakao", idTokens.kakao(sub, clock.instant()));
			withdraw(again.get("accessToken").asString()).andExpect(status().isOk());
		}

		/* 구글 */

		@Test
		void 구글_회원은_googleAuthCode_로_받은_토큰으로_연결을_해제한다() throws Exception {
			String sub = uniqueSub();
			JsonNode tokens = login("google", idTokens.google(sub, clock.instant()));
			String code = googleAuthCodeFor(sub);

			withdraw(tokens.get("accessToken").asString(), code)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.withdrawn").value(true));

			assertThat(googleOAuthClient.revoked()).containsExactly("refresh-" + code);
			assertThat(kakaoUnlinkClient.unlinked()).isEmpty();
		}

		@Test
		void 구글_회원이_googleAuthCode_없이_탈퇴하면_C001_이고_탈퇴되지_않는다() throws Exception {
			String accessToken = login("google", idTokens.google(uniqueSub(), clock.instant()))
				.get("accessToken").asString();

			withdraw(accessToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
			getMe(accessToken).andExpect(status().isOk());
		}

		@Test
		void 교환할_수_없는_googleAuthCode_면_A029_이고_탈퇴되지_않는다() throws Exception {
			String accessToken = login("google", idTokens.google(uniqueSub(), clock.instant()))
				.get("accessToken").asString();

			withdraw(accessToken, "unknown-code")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A029"));
			getMe(accessToken).andExpect(status().isOk());
		}

		@Test
		void 다른_구글_계정의_googleAuthCode_면_A029() throws Exception {
			String accessToken = login("google", idTokens.google(uniqueSub(), clock.instant()))
				.get("accessToken").asString();

			withdraw(accessToken, googleAuthCodeFor(uniqueSub()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A029"));
			assertThat(googleOAuthClient.revoked()).isEmpty();
		}

		@Test
		void 구글_해제가_실패해도_탈퇴는_성공한다() throws Exception {
			String sub = uniqueSub();
			String accessToken = login("google", idTokens.google(sub, clock.instant())).get("accessToken").asString();
			googleOAuthClient.failingRevoke(true);

			withdraw(accessToken, googleAuthCodeFor(sub)).andExpect(status().isOk());
			getMe(accessToken).andExpect(jsonPath("$.code").value("A003"));
		}

		@Test
		void 카카오_연결_끊기가_실패해도_탈퇴는_성공하고_나중에_다시_시도한다() throws Exception {
			String sub = uniqueSub();
			JsonNode tokens = login("kakao", idTokens.kakao(sub, clock.instant()));
			kakaoUnlinkClient.failing(true);

			withdraw(tokens.get("accessToken").asString()).andExpect(status().isOk());
			assertThat(unlinkTaskStore.findAll()).anyMatch(task -> task.providerSubject().equals(sub));

			kakaoUnlinkClient.failing(false);
			providerUnlinkService.retryFailed();

			assertThat(kakaoUnlinkClient.unlinked()).contains(sub);
			assertThat(unlinkTaskStore.findAll()).noneMatch(task -> task.providerSubject().equals(sub));
		}

		@Test
		void 액세스_토큰이_없으면_A001() throws Exception {
			withdraw(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
		}
	}

	private JsonNode data(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
	}
}
