package com.ssafy.yeogirok.domain.course.seed;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 투어API 공식 코스 적재 설정(명세 {@code specs/tour-course-seed.md} 6-2). 기본은 꺼짐이고, 켜져 있을 때만 서버 시작 뒤 한 번 돈다.
 *
 * @param location    S3 면 사진 버킷 안 접두어(예 {@code course-seed/}), 로컬이면 폴더 경로
 * @param ownerUserId 코스 주인 회원 번호. 비면 시스템 계정
 * @param unpublish   내릴 contentId 목록. {@code ALL} 이면 공식 코스 전부
 * @param reseed      다시 넣을 contentId 목록
 */
@ConfigurationProperties("yeogirok.course-seed")
public record CourseSeedProperties(boolean enabled, Source source, String location, Long ownerUserId,
	List<String> unpublish, List<String> reseed) {

	public enum Source { S3, LOCAL }

	public static final String ALL = "ALL";

	public CourseSeedProperties {
		source = source == null ? Source.S3 : source;
		location = location == null || location.isBlank() ? null : location.strip();
		unpublish = clean(unpublish);
		reseed = clean(reseed);
	}

	public boolean unpublishAll() {
		return unpublish.contains(ALL);
	}

	private static List<String> clean(List<String> values) {
		return values == null ? List.of() : values.stream().map(String::strip).filter(value -> !value.isEmpty()).toList();
	}
}
