package com.ssafy.yeogirok.domain.course.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberView;

import lombok.RequiredArgsConstructor;

/**
 * 공개 코스 작성자: 원본 방의 현재 참여자 중 탈퇴하지 않은 회원, userId 오름차순(명세 {@code specs/course-publish-api.md} 1-6).
 * 여행방 쪽 현재 참여자 조회가 이미 탈퇴자를 뺀다.
 */
@Component
@RequiredArgsConstructor
public class CourseAuthors {

	private final RoomMemberRepository roomMemberRepository;

	public List<PublicAuthor> of(Long roomId) {
		return byRoom(List.of(roomId)).getOrDefault(roomId, List.of());
	}

	/** 여러 방을 한 번에 읽는다(API-074). 참여자가 없는 방은 결과에 없다. */
	public Map<Long, List<PublicAuthor>> byRoom(Collection<Long> roomIds) {
		if (roomIds.isEmpty()) {
			return Map.of();
		}
		return roomMemberRepository.findCurrentMembers(roomIds).stream()
			.sorted(Comparator.comparing(RoomMemberView::userId))
			.collect(groupingBy(RoomMemberView::roomId,
				mapping(member -> new PublicAuthor(member.userId(), member.profileImageUrl()), toList())));
	}
}
