package com.ssafy.yeogirok.domain.applink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.yeogirok.domain.room.invite.RoomInviteProperties;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** App Links 확인 파일과 초대 안내 페이지. 로그인 없이 열리는지와 응답 모양을 본다. */
@WebMvcTest({AssetLinksController.class, InvitePageController.class})
@Import(WebMvcSecurity.class)
@EnableConfigurationProperties({AndroidAppLinkProperties.class, RoomInviteProperties.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
	"yeogirok.app-links.android.package-name=com.ssafya109.yeogirok",
	"yeogirok.app-links.android.sha256-cert-fingerprints="
		+ "FA:C6:17:45:DC:09:03:78:6F:B9:ED:E6:2A:96:2B:39:9F:73:48:F0:BB:6F:89:9B:83:32:66:75:91:03:3B:9C,"
		+ "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"
})
class AppLinkWebTest {

	private static final String TOKEN = "Ab3_-xYz0123456789Ab3_-xYz0123456789Ab3_-xY";

	@Autowired
	MockMvc mockMvc;

	@Test
	void assetlinks는_로그인_없이_Digital_Asset_Links_형식으로_준다() throws Exception {
		mockMvc.perform(get("/.well-known/assetlinks.json"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("""
				[{
				  "relation": ["delegate_permission/common.handle_all_urls"],
				  "target": {
				    "namespace": "android_app",
				    "package_name": "com.ssafya109.yeogirok",
				    "sha256_cert_fingerprints": [
				      "FA:C6:17:45:DC:09:03:78:6F:B9:ED:E6:2A:96:2B:39:9F:73:48:F0:BB:6F:89:9B:83:32:66:75:91:03:3B:9C",
				      "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"
				    ]
				  }
				}]
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 만료된_토큰_헤더가_붙어도_공개_경로는_401이_아니다() throws Exception {
		mockMvc.perform(get("/.well-known/assetlinks.json").header("Authorization", "Bearer expired"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/invite/" + TOKEN).header("Authorization", "Bearer expired"))
			.andExpect(status().isOk());
	}

	@Test
	void 초대_페이지는_같은_https_주소를_앱으로_여는_intent_버튼을_준다() throws Exception {
		String html = mockMvc.perform(get("/invite/" + TOKEN))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("intent://test.yeogirok/invite/" + TOKEN
			+ "#Intent;scheme=https;package=com.ssafya109.yeogirok;end");
	}

	@Test
	void 토큰_모양이_아니면_버튼을_만들지_않고_입력을_그대로_싣지_않는다() throws Exception {
		String html = mockMvc.perform(get("/invite/{token}", "<img src=x onerror=alert(1)>"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(html).doesNotContain("intent://").doesNotContain("<img").contains("링크 주소가 올바르지 않아요");
	}

	@Test
	void 공개_경로는_GET만_연다() throws Exception {
		mockMvc.perform(post("/invite/" + TOKEN)).andExpect(status().isForbidden());
		mockMvc.perform(get("/invite/a/b")).andExpect(status().isForbidden());
	}
}
