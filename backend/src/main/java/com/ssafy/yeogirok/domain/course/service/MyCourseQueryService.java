package com.ssafy.yeogirok.domain.course.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.course.dto.MyCourseListResponse;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.record.service.RecordCursor;
import com.ssafy.yeogirok.global.request.PageSize;

import lombok.RequiredArgsConstructor;

/** 내가 현재 참여 중인 기록의 공개 코스 목록(API-074). 내가 게시하지 않았어도 나온다. */
@Service
@RequiredArgsConstructor
public class MyCourseQueryService {

	private final TravelRecordRepository travelRecordRepository;
	private final CourseAuthors courseAuthors;

	/** 기록 id 내림차순, limit 기본 20·최대 50(C007), 커서 C006. 058 과 같다(명세 5절 8). 작성자는 방을 모아 한 번에 읽는다. */
	@Transactional(readOnly = true)
	public MyCourseListResponse getMine(Long userId, String cursor, Integer limit) {
		int size = PageSize.resolve(limit);
		RecordCursor after = RecordCursor.decodeOrNull(cursor);
		List<TravelRecord> found = travelRecordRepository.findJoinedPublishedRecords(userId,
			after == null ? Long.MAX_VALUE : after.recordId(), Limit.of(size + 1));
		List<TravelRecord> page = found.size() > size ? found.subList(0, size) : found;
		Map<Long, List<PublicAuthor>> authors = courseAuthors.byRoom(page.stream().map(TravelRecord::getRoomId).toList());
		List<MyCourseListResponse.Item> items = page.stream()
			.map(record -> new MyCourseListResponse.Item(record.getId(), record.getTitle(),
				authors.getOrDefault(record.getRoomId(), List.of()), record.getPublishedAt(), record.getVersion()))
			.toList();
		String nextCursor = found.size() > size ? new RecordCursor(page.getLast().getId()).encode() : null;
		return new MyCourseListResponse(items, nextCursor);
	}
}
