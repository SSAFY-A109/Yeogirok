package com.ssafy.yeogirok.domain.course.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 미리보기(API-072)에서 본 내용과 저장(073·075) 시점 내용이 같은지 보는 값(명세 {@code specs/course-publish-api.md} 1-3).
 * 서버만 계산하고 비교만 하므로 서명은 넣지 않는다. 작성자·사진 URL 은 넣지 않는다.
 */
public final class SelectionHash {

	/** 구분 문자(단위 구분자). 요청 검사가 제목·설명의 제어 문자를 막아 값 안에 나오지 않는다({@code ControlCharacters}). */
	private static final String SEPARATOR = "\u001F";

	private SelectionHash() {
	}

	/**
	 * 072·073·075 가 같은 입력으로 해시를 만들도록 모은 곳이다. 해시 입력을 바꿀 때는 여기만 고친다(PR #89 리뷰).
	 *
	 * @param requested 요청의 설명. 생략(null)이면 지금 기록 설명을 쓴다(명세 1-2)
	 */
	public static String description(TravelRecord record, String requested) {
		return requested == null ? record.getDescription() : requested;
	}

	/** 기록의 지금 id·version 으로 계산한다(072 미리보기가 돌려주는 값). */
	public static String of(TravelRecord record, String title, String description, boolean includePhotos,
		CourseSnapshot snapshot) {
		return of(record.getId(), record.getVersion(), title, description, includePhotos, snapshot);
	}

	/** 지금 값으로 다시 계산한 해시가 요청과 다르면 CO010(073·075). */
	public static void verify(TravelRecord record, String title, String description, boolean includePhotos,
		CourseSnapshot snapshot, String requested) {
		if (!of(record, title, description, includePhotos, snapshot).equals(requested)) {
			throw new BusinessException(ExceptionType.COURSE_SELECTION_CHANGED);
		}
	}

	/** @return SHA-256 16진 소문자 */
	public static String of(Long recordId, int version, String title, String description, boolean includePhotos,
		CourseSnapshot snapshot) {
		StringJoiner input = new StringJoiner(SEPARATOR);
		input.add(recordId.toString()).add(Integer.toString(version)).add(title).add(description)
			.add(Boolean.toString(includePhotos));
		for (ActualVisit visit : snapshot.visits()) {
			input.add("V" + visit.getId() + ":" + visit.getVisitDate() + ":" + visit.getPosition() + ":"
				+ visit.getPlaceId());
		}
		if (includePhotos) {
			for (VisitImage image : snapshot.linkedPhotos()) {
				input.add("P" + image.getId() + ":" + image.getVisitId() + ":" + image.getPosition());
			}
		}
		return sha256(input.toString());
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없습니다.", e);
		}
	}
}
