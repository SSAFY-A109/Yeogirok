package com.ssafy.yeogirok.domain.applink;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Android App Links 설정. 안드로이드가 초대 링크(https://도메인/invite/...)를 앱으로 열어도 되는지 확인할 때 쓴다.
 *
 * @param packageName            앱 패키지 이름(frontend/app.json 의 android.package)
 * @param appScheme              앱 커스텀 스킴(frontend/app.json 의 scheme). 초대 안내 페이지의 앱 열기 버튼이 쓴다
 * @param sha256CertFingerprints 앱 서명 인증서 SHA-256 지문. {@code AA:BB:...} 32바이트. 디버그·배포 키처럼 여러 개를 쉼표로 넣는다.
 *                               비어 있으면 assetlinks.json 이 빈 배열이라 링크가 앱으로 열리지 않는다
 */
@Validated
@ConfigurationProperties("yeogirok.app-links.android")
public record AndroidAppLinkProperties(
	@NotBlank String packageName,
	@NotBlank @Pattern(regexp = "^[a-z][a-z0-9+.-]*$", message = "스킴은 소문자로 시작하는 URI 스킴이어야 합니다") String appScheme,
	@NotNull List<@Pattern(regexp = "^([0-9A-F]{2}:){31}[0-9A-F]{2}$",
		message = "SHA-256 지문은 AA:BB:... 형식(대문자 16진수 32바이트)이어야 합니다") String> sha256CertFingerprints
) {
}
