package com.ssafy.yeogirok.domain.file;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트. 자격 증명은 SDK 기본 순서(로컬 AWS_PROFILE, 서버 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY)로 찾는다.
 * 자격 증명은 실제 S3 를 부를 때 찾으므로 키가 없어도 앱은 뜬다.
 */
@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileStorageConfig {

	@Bean
	public S3Client s3Client(FileProperties properties) {
		return S3Client.builder().region(Region.of(properties.region())).build();
	}

	@Bean
	public S3Presigner s3Presigner(FileProperties properties) {
		return S3Presigner.builder().region(Region.of(properties.region())).build();
	}
}
