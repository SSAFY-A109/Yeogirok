package com.ssafy.yeogirok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestAuthConfig;
import com.ssafy.yeogirok.support.TestIdTokens;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAuthConfig.class)
class AuthApiTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JsonMapper jsonMapper;
	@Autowired
	TestIdTokens idTokens;
	@Autowired
	MutableClock clock;
	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void resetClock() {
		clock.set(TestAuthConfig.START);
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions login(String provider, String idToken, String nonce) throws Exception {
		String nonceField = nonce == null ? "" : ", \"nonce\": \"%s\"".formatted(nonce);
		return postJson("/api/v1/auth/token",
			"{\"provider\": \"%s\", \"idToken\": \"%s\"%s}".formatted(provider, idToken, nonceField));
	}

	private JsonNode data(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
	}

	private JsonNode loginGoogle(String subject) throws Exception {
		return data(login("google", idTokens.google(subject, clock.instant()), TestIdTokens.NONCE).andExpect(status().isOk()));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return postJson("/api/v1/auth/token/refresh", "{\"refreshToken\": \"%s\"}".formatted(refreshToken));
	}

	private ResultActions logout(String accessToken, String refreshToken) throws Exception {
		var request = post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\": \"%s\"}".formatted(refreshToken));
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		return mockMvc.perform(request);
	}

	private static String uniqueSub() {
		return UUID.randomUUID().toString();
	}

	private static void expectFailure(ResultActions result, int status, String code) throws Exception {
		result.andExpect(status().is(status))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").isString());
	}

	/* ── API-005 ──────────────────────────────────────────────── */

	@Nested
	class 로그인 {

		@Test
		void 처음_로그인하면_가입되고_isNewUser_가_true() throws Exception {
			String sub = uniqueSub();

			login("google", idTokens.google(sub, clock.instant()), TestIdTokens.NONCE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").isString())
				.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
				.andExpect(jsonPath("$.data.refreshToken").isString())
				.andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(Duration.ofDays(14).toSeconds()))
				.andExpect(jsonPath("$.data.isNewUser").value(true));

			var user = userRepository.findByProviderAndProviderSubject(OAuthProvider.GOOGLE, sub).orElseThrow();
			assertThat(user.getNickname()).isEqualTo("닉네임-" + sub);
			assertThat(user.getEmail()).isEqualTo(sub + "@example.com");
		}

		@Test
		void 같은_사람이_다시_로그인하면_isNewUser_가_false() throws Exception {
			String sub = uniqueSub();
			loginGoogle(sub);

			login("google", idTokens.google(sub, clock.instant()), TestIdTokens.NONCE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isNewUser").value(false));
		}

		@Test
		void 카카오도_같은_흐름으로_로그인된다() throws Exception {
			String sub = "1234567890";
			login("kakao", idTokens.kakao(sub, clock.instant()), TestIdTokens.NONCE)
				.andExpect(status().isOk());

			assertThat(userRepository.findByProviderAndProviderSubject(OAuthProvider.KAKAO, sub)).isPresent();
		}

		@Test
		void 처음_로그인하는_계정이_동시에_와도_회원은_하나만_생긴다() throws Exception {
			String sub = uniqueSub();
			int requests = 5;
			ExecutorService executor = Executors.newFixedThreadPool(requests);
			CountDownLatch start = new CountDownLatch(1);
			try {
				List<Future<JsonNode>> results = new ArrayList<>();
				for (int i = 0; i < requests; i++) {
					String idToken = idTokens.google(sub, clock.instant());
					results.add(executor.submit(() -> {
						start.await();
						return data(login("google", idToken, TestIdTokens.NONCE).andExpect(status().isOk()));
					}));
				}
				start.countDown();

				List<Boolean> isNewUser = new ArrayList<>();
				for (Future<JsonNode> result : results) {
					isNewUser.add(result.get().get("isNewUser").asBoolean());
				}
				assertThat(isNewUser).containsOnlyOnce(true);
				assertThat(userRepository.findByProviderAndProviderSubject(OAuthProvider.GOOGLE, sub)).isPresent();
			} finally {
				executor.shutdownNow();
			}
		}

		@Test
		void 지원하지_않는_provider_는_C003() throws Exception {
			expectFailure(login("naver", idTokens.google(uniqueSub(), clock.instant()), TestIdTokens.NONCE), 400, "C003");
		}

		@Test
		void idToken_이_없으면_C001() throws Exception {
			expectFailure(postJson("/api/v1/auth/token", "{\"provider\": \"google\"}"), 400, "C001");
		}

		@Test
		void JSON_이_아니면_C002() throws Exception {
			expectFailure(postJson("/api/v1/auth/token", "not-json"), 400, "C002");
		}

		@Test
		void 다른_앱용으로_발급된_토큰은_거부() throws Exception {
			String token = idTokens.create(OAuthProvider.GOOGLE, uniqueSub(), clock.instant(),
				claims -> claims.audience(List.of("someone-elses-client-id")));
			expectFailure(login("google", token, TestIdTokens.NONCE), 401, "A005");
		}

		@Test
		void 구글_토큰을_카카오로_보내면_거부() throws Exception {
			expectFailure(login("kakao", idTokens.google(uniqueSub(), clock.instant()), TestIdTokens.NONCE), 401,
				"A005");
		}

		@Test
		void 만료된_ID_토큰은_거부() throws Exception {
			String token = idTokens.google(uniqueSub(), clock.instant());
			clock.advance(Duration.ofHours(2));
			expectFailure(login("google", token, TestIdTokens.NONCE), 401, "A005");
		}

		@Test
		void 서명이_틀리면_거부() throws Exception {
			String forged = new TestIdTokens().google(uniqueSub(), clock.instant());
			expectFailure(login("google", forged, TestIdTokens.NONCE), 401, "A005");
		}

		@Test
		void nonce_가_같으면_통과() throws Exception {
			String token = idTokens.create(OAuthProvider.KAKAO, uniqueSub(), clock.instant(),
				claims -> claims.claim("nonce", "n-123"));
			login("kakao", token, "n-123").andExpect(status().isOk());
		}

		@Test
		void nonce_가_다르면_거부() throws Exception {
			String token = idTokens.create(OAuthProvider.KAKAO, uniqueSub(), clock.instant(),
				claims -> claims.claim("nonce", "n-123"));
			expectFailure(login("kakao", token, "other"), 401, "A006");
		}

		@Test
		void nonce_가_없으면_C001() throws Exception {
			String token = idTokens.kakao(uniqueSub(), clock.instant());
			expectFailure(login("kakao", token, null), 400, "C001");
		}

		@Test
		void ID_토큰에_nonce_가_없으면_거부() throws Exception {
			String token = idTokens.create(OAuthProvider.KAKAO, uniqueSub(), clock.instant(), claims -> {
			});
			expectFailure(login("kakao", token, TestIdTokens.NONCE), 401, "A006");
		}
	}

	/* ── API-006 ──────────────────────────────────────────────── */

	@Nested
	class 재발급 {

		@Test
		void 재발급하면_리프레시_토큰도_바뀐다() throws Exception {
			JsonNode tokens = loginGoogle(uniqueSub());

			JsonNode refreshed = data(refresh(tokens.get("refreshToken").asString())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isNewUser").doesNotExist()));

			assertThat(refreshed.get("refreshToken").asString()).isNotEqualTo(tokens.get("refreshToken").asString());
		}

		@Test
		void 이미_쓴_리프레시_토큰을_다시_쓰면_A010_이고_그_세션_전체가_끊긴다() throws Exception {
			String first = loginGoogle(uniqueSub()).get("refreshToken").asString();
			String second = data(refresh(first).andExpect(status().isOk())).get("refreshToken").asString();

			expectFailure(refresh(first), 401, "A010");
			// 재사용을 감지하면 세션을 폐기한다 — 정상 사용자 쪽 최신 토큰도 끊겨야 한다.
			expectFailure(refresh(second), 401, "A009");
		}

		@Test
		void 같은_리프레시_토큰으로_동시에_재발급하면_한_번만_성공한다() throws Exception {
			String refreshToken = loginGoogle(uniqueSub()).get("refreshToken").asString();
			int requests = 5;
			ExecutorService executor = Executors.newFixedThreadPool(requests);
			CountDownLatch start = new CountDownLatch(1);
			try {
				List<Future<Integer>> results = new ArrayList<>();
				for (int i = 0; i < requests; i++) {
					results.add(executor.submit(() -> {
						start.await();
						return refresh(refreshToken).andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();

				List<Integer> statuses = new ArrayList<>();
				for (Future<Integer> result : results) {
					statuses.add(result.get());
				}
				assertThat(statuses).containsOnlyOnce(200);
				assertThat(statuses).filteredOn(code -> code != 200).containsOnly(401);
			} finally {
				executor.shutdownNow();
			}
		}

		@Test
		void 만료된_리프레시_토큰은_A008() throws Exception {
			String refreshToken = loginGoogle(uniqueSub()).get("refreshToken").asString();
			clock.advance(Duration.ofDays(15));
			expectFailure(refresh(refreshToken), 401, "A008");
		}

		@Test
		void 모르는_리프레시_토큰은_A007() throws Exception {
			expectFailure(refresh("unknown-token"), 401, "A007");
		}

		@Test
		void 리프레시_토큰이_없으면_C001() throws Exception {
			expectFailure(postJson("/api/v1/auth/token/refresh", "{}"), 400, "C001");
		}
	}

	/* ── API-007 과 액세스 토큰 401 세 갈래 ─────────────────────── */

	@Nested
	class 로그아웃과_액세스_토큰 {

		@Test
		void 로그아웃하면_data_없이_성공하고_그_리프레시_토큰은_A009() throws Exception {
			JsonNode tokens = loginGoogle(uniqueSub());
			String accessToken = tokens.get("accessToken").asString();
			String refreshToken = tokens.get("refreshToken").asString();

			String body = logout(accessToken, refreshToken)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
			assertThat(jsonMapper.readTree(body).properties())
				.extracting(java.util.Map.Entry::getKey)
				.containsExactly("success");

			expectFailure(refresh(refreshToken), 401, "A009");
		}

		@Test
		void 로그아웃은_여러_번_해도_성공한다() throws Exception {
			JsonNode tokens = loginGoogle(uniqueSub());
			String accessToken = tokens.get("accessToken").asString();
			String refreshToken = tokens.get("refreshToken").asString();

			logout(accessToken, refreshToken).andExpect(status().isOk());
			logout(accessToken, refreshToken).andExpect(status().isOk());
		}

		@Test
		void 다른_사람의_리프레시_토큰으로는_로그아웃시키지_못한다() throws Exception {
			String attackerAccess = loginGoogle(uniqueSub()).get("accessToken").asString();
			String victimRefresh = loginGoogle(uniqueSub()).get("refreshToken").asString();

			logout(attackerAccess, victimRefresh).andExpect(status().isOk());
			refresh(victimRefresh).andExpect(status().isOk());
		}

		@Test
		void 액세스_토큰이_없으면_A001() throws Exception {
			expectFailure(logout(null, "x"), 401, "A001");
		}

		@Test
		void 액세스_토큰이_만료되면_A002() throws Exception {
			String accessToken = loginGoogle(uniqueSub()).get("accessToken").asString();
			clock.advance(Duration.ofMinutes(32));
			expectFailure(logout(accessToken, "x"), 401, "A002");
		}

		@Test
		void 액세스_토큰이_위조되면_A003() throws Exception {
			String accessToken = loginGoogle(uniqueSub()).get("accessToken").asString();
			String tampered = accessToken.substring(0, accessToken.length() - 2) + "xx";
			expectFailure(logout(tampered, "x"), 401, "A003");
		}

		@Test
		void 액세스_토큰_자리에_아무_문자열이나_넣으면_A003() throws Exception {
			expectFailure(logout("garbage", "x"), 401, "A003");
		}
	}
}
