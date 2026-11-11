package com.ssafy.yeogirok.domain.applink;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/**
 * Android App Links 확인 파일. 안드로이드가 앱 설치·업데이트 때 이 주소를 직접 가져가 도메인과 앱을 묶는다.
 * 로그인 없이, 리다이렉트 없이 200 과 application/json 으로 줘야 한다(SecurityConfig 의 공개 웹 체인).
 * 공통 응답 봉투를 씌우지 않는다. 형식은 Google Digital Asset Links 규격이다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class AssetLinksController {

	private final AndroidAppLinkProperties properties;

	@GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Map<String, Object>>> assetLinks() {
		if (properties.sha256CertFingerprints().isEmpty()) {
			return ResponseEntity.ok(List.of());
		}
		return ResponseEntity.ok(List.of(Map.of(
			"relation", List.of("delegate_permission/common.handle_all_urls"),
			"target", Map.of(
				"namespace", "android_app",
				"package_name", properties.packageName(),
				"sha256_cert_fingerprints", properties.sha256CertFingerprints()))));
	}
}
