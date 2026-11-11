package com.ssafy.yeogirok.domain.applink;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import com.ssafy.yeogirok.domain.room.invite.RoomInviteProperties;
import com.ssafy.yeogirok.domain.room.invite.RoomInviteTokenCipher;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/**
 * 초대 링크(API-024 inviteUrl)를 앱이 없는 기기나 App Links 가 아직 확인되지 않은 기기에서 열었을 때 보이는 안내 페이지.
 * 앱이 설치되어 링크가 확인되면 안드로이드가 이 페이지 대신 앱을 연다.
 *
 * <p>토큰으로 DB 를 조회하지 않는다. 로그인 없이 열리는 페이지라 방 정보를 드러내지 않고, 토큰이 맞는지도 알려 주지 않는다.
 * 참여 확인과 토큰 검증은 앱이 API-025 로 한다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class InvitePageController {

	/** 우리가 만드는 토큰 모양(URL-safe Base64, 패딩 없음). 이 밖의 값은 앱 열기 버튼을 만들지 않는다. */
	private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{1," + RoomInviteTokenCipher.TOKEN_LENGTH + "}$");

	private final AndroidAppLinkProperties properties;
	private final RoomInviteProperties inviteProperties;

	/** 앱을 못 찾았을 때 intent 가 되돌아오는 표시. 이 값이면 설치 안내를 함께 보여 준다. */
	static final String APP_MISSING = "missing";

	@GetMapping(value = "/invite/{token}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
	public ResponseEntity<String> invitePage(@PathVariable String token,
		@RequestParam(name = "app", required = false) String app) {
		String missingNotice = APP_MISSING.equals(app)
			? "<p class=\"notice\">이 기기에서 여기록 앱을 찾지 못했어요. 앱을 설치하거나 최신 버전으로 업데이트한 뒤 다시 눌러 주세요.</p>"
			: "";
		String openButton = TOKEN.matcher(token).matches()
			? missingNotice + "<a class=\"button\" href=\"" + HtmlUtils.htmlEscape(intentUrl(token)) + "\">여기록 앱에서 열기</a>"
			: "<p class=\"muted\">링크 주소가 올바르지 않아요. 초대한 사람에게 링크를 다시 받아 주세요.</p>";
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header("Referrer-Policy", "no-referrer")
			.header("X-Robots-Tag", "noindex")
			.body(PAGE.formatted(openButton));
	}

	/**
	 * 안드로이드 크롬·카카오톡 인앱 브라우저에서 앱을 여는 intent 주소. 앱의 커스텀 스킴({@code yeogirokfe://invite/{token}})으로 연다.
	 * https App Links 는 앱 빌드에 /invite intentFilter 가 들어 있어야만 열리지만, 스킴은 처음 빌드부터 등록되어 있어
	 * 예전 빌드에서도 열린다. 앱은 Play 스토어에 없어서, 앱을 못 찾으면 스토어 대신 이 페이지(?app=missing)로 되돌아온다.
	 */
	private String intentUrl(String token) {
		URI base = URI.create(inviteProperties.urlBase());
		String fallback = inviteProperties.urlBase() + "/" + token + "?app=" + APP_MISSING;
		return "intent://" + base.getRawPath().replaceFirst("^/", "") + "/" + token
			+ "#Intent;scheme=" + properties.appScheme() + ";package=" + properties.packageName()
			+ ";S.browser_fallback_url=" + URLEncoder.encode(fallback, StandardCharsets.UTF_8) + ";end";
	}

	private static final String PAGE = """
		<!doctype html>
		<html lang="ko">
		<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<meta name="robots" content="noindex">
		<title>여기록 초대</title>
		<style>
		body{margin:0;font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;background:#F5F7FA;color:#1A1D21}
		main{max-width:420px;margin:0 auto;padding:64px 24px;text-align:center;word-break:keep-all}
		h1{font-size:22px;margin:0 0 12px}
		p{font-size:15px;line-height:1.6;margin:0 0 24px}
		.button{display:block;padding:16px;border-radius:12px;background:#208AEF;color:#fff;font-weight:600;text-decoration:none}
		.muted{color:#6B7280}
		.notice{padding:12px 16px;border-radius:12px;background:#FEF3C7;color:#92400E;font-size:14px}
		</style>
		</head>
		<body>
		<main>
		<h1>여행방에 초대받았어요</h1>
		<p>여기록 앱에서 초대를 확인하고 참여할 수 있어요.</p>
		%s
		</main>
		</body>
		</html>
		""";
}
