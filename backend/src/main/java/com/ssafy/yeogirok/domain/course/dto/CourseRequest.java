package com.ssafy.yeogirok.domain.course.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.nonNullBoolean;
import static com.ssafy.yeogirok.global.request.JsonFields.nonNullString;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalNonNullBoolean;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalNonNullString;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.record.dto.RecordTitle;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * 코스 공개 요청 본문(API-072·073·075, 명세 {@code specs/course-publish-api.md} 1-2). 공개 대상은 기록 전체라 부분 선택
 * 필드(visitIds 등)를 보내면 null 이어도 C003.
 */
public class CourseRequest {

	/** 설명 최대 길이(코드 포인트). 노션에 없는 값(명세 5절 3). */
	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	@JsonProperty("version")
	private JsonNode version;
	@JsonProperty("title")
	private JsonNode title;
	@JsonProperty("description")
	private JsonNode description;
	@JsonProperty("includePhotos")
	private JsonNode includePhotos;
	@JsonProperty("selectionHash")
	private JsonNode selectionHash;
	@JsonProperty("published")
	private JsonNode published;
	@JsonProperty("visitIds")
	private JsonNode visitIds;
	@JsonProperty("photoIds")
	private JsonNode photoIds;
	@JsonProperty("storyIds")
	private JsonNode storyIds;
	@JsonProperty("visibility")
	private JsonNode visibility;
	@JsonProperty("publishedAt")
	private JsonNode publishedAt;

	/** API-072. title·includePhotos·version 필수. */
	public CourseInput toPreview() {
		rejectSelectionFields();
		return new CourseInput(requiredInt(version, "version"), RecordTitle.normalize(nonNullString(title, "title")),
			description(), nonNullBoolean(includePhotos, "includePhotos"), null, null);
	}

	/** API-073. 072 에 selectionHash 필수. */
	public CourseInput toPublish() {
		CourseInput preview = toPreview();
		return new CourseInput(preview.version(), preview.title(), preview.description(), preview.includePhotos(),
			nonNullString(selectionHash, "selectionHash"), null);
	}

	/** API-075. version 만 필수. 보낸 값은 null 이면 C001. */
	public CourseInput toUpdate() {
		rejectSelectionFields();
		String newTitle = optionalNonNullString(title, "title");
		return new CourseInput(requiredInt(version, "version"), newTitle == null ? null : RecordTitle.normalize(newTitle),
			description(), optionalNonNullBoolean(includePhotos, "includePhotos"),
			optionalNonNullString(selectionHash, "selectionHash"), optionalNonNullBoolean(published, "published"));
	}

	/** 생략이면 null, null·문자열 아님 C002, 2,000자 초과 C003. 앞뒤 공백은 그대로 둔다. */
	private String description() {
		if (description == null) {
			return null;
		}
		if (!description.isString()) {
			throw invalidFormat("description", "문자열이어야 합니다.");
		}
		String value = description.stringValue();
		if (value.codePointCount(0, value.length()) > DESCRIPTION_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"description: 최대 %d자입니다.".formatted(DESCRIPTION_MAX_LENGTH));
		}
		return value;
	}

	private void rejectSelectionFields() {
		reject("visitIds", visitIds);
		reject("photoIds", photoIds);
		reject("storyIds", storyIds);
		reject("visibility", visibility);
		reject("publishedAt", publishedAt);
	}

	private static void reject(String field, JsonNode value) {
		if (value != null) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				field + ": 공개 코스는 기록 전체를 공개해 받지 않는 필드입니다.");
		}
	}
}
