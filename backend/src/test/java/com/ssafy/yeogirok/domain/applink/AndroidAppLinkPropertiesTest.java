package com.ssafy.yeogirok.domain.applink;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** 서버 환경 변수에 잘못된 지문이 들어가면 조용히 빈 파일을 주지 않고 기동 단계에서 멈춘다. */
class AndroidAppLinkPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("yeogirok.app-links.android.package-name=com.ssafya109.yeogirok",
			"yeogirok.app-links.android.app-scheme=yeogirokfe");

	@Test
	void 지문이_비어_있으면_빈_목록() {
		runner.withPropertyValues("yeogirok.app-links.android.sha256-cert-fingerprints=")
			.run(context -> assertThat(context.getBean(AndroidAppLinkProperties.class).sha256CertFingerprints()).isEmpty());
	}

	@Test
	void 쉼표로_여러_개를_넣는다() {
		runner.withPropertyValues("yeogirok.app-links.android.sha256-cert-fingerprints="
				+ "FA:C6:17:45:DC:09:03:78:6F:B9:ED:E6:2A:96:2B:39:9F:73:48:F0:BB:6F:89:9B:83:32:66:75:91:03:3B:9C,"
				+ "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF")
			.run(context -> assertThat(context.getBean(AndroidAppLinkProperties.class).sha256CertFingerprints()).hasSize(2));
	}

	@Test
	void 형식이_틀린_지문은_기동을_막는다() {
		runner.withPropertyValues("yeogirok.app-links.android.sha256-cert-fingerprints=fa:c6:17")
			.run(context -> assertThat(context).hasFailed());
	}

	/** application.yaml 기본값. 지문에 콜론이 있어도 ${...:기본값} 의 첫 콜론 뒤 전체가 기본값으로 읽힌다. */
	@Test
	void 환경_변수가_없으면_application_yaml의_debug_키_지문을_쓴다() throws Exception {
		var yaml = new org.springframework.boot.env.YamlPropertySourceLoader()
			.load("application", new org.springframework.core.io.ClassPathResource("application.yaml")).getFirst();
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.withInitializer(context -> context.getEnvironment().getPropertySources().addLast(yaml))
			.run(context -> {
				AndroidAppLinkProperties properties = context.getBean(AndroidAppLinkProperties.class);
				assertThat(properties.packageName()).isEqualTo("com.ssafya109.yeogirok");
				assertThat(properties.appScheme()).isEqualTo("yeogirokfe");
				assertThat(properties.sha256CertFingerprints()).containsExactly(
					"FA:C6:17:45:DC:09:03:78:6F:B9:ED:E6:2A:96:2B:39:9F:73:48:F0:BB:6F:89:9B:83:32:66:75:91:03:3B:9C");
			});
	}

	@Configuration
	@EnableConfigurationProperties(AndroidAppLinkProperties.class)
	static class Config {
	}
}
