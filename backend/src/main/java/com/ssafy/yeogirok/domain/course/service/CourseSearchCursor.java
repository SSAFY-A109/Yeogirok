package com.ssafy.yeogirok.domain.course.service;

/** API-014 커서. 정렬 키(가져간 방 수 내림차순, 기록 id 내림차순)의 마지막 값 "{importCount}:{id}"({@link CourseCursors}). */
record CourseSearchCursor(long importCount, long id) {

	String encode() {
		return CourseCursors.encode(importCount, id);
	}

	/** 정렬상 이 커서 뒤에 오는지(가져간 방 수 내림차순, 같으면 id 내림차순). */
	boolean precedes(long otherImportCount, long otherId) {
		return otherImportCount < importCount || otherImportCount == importCount && otherId < id;
	}

	/** @return 값이 없으면 null */
	static CourseSearchCursor decodeOrNull(String cursor) {
		long[] parts = CourseCursors.decode(cursor, 2);
		return parts == null ? null : new CourseSearchCursor(parts[0], parts[1]);
	}
}
