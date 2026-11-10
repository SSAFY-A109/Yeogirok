package com.ssafy.yeogirok.domain.record.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.record.repository.ActiveUserRepository;

import lombok.RequiredArgsConstructor;

/** 기록 응답의 작성자·업로더 표시. 탈퇴했거나 없는 회원의 ID 는 null 로 감춘다(노션 059). */
@Component
@RequiredArgsConstructor
public class RecordAuthors {

	private final ActiveUserRepository activeUserRepository;

	/** @return 회원 ID 를 받아 탈퇴하지 않은 회원이면 그대로, 아니면 null 을 돌려주는 함수(회원 상태는 한 번에 읽는다) */
	public UnaryOperator<Long> shownIds(Collection<Long> userIds) {
		Set<Long> active = userIds.isEmpty() ? Set.of() : new HashSet<>(activeUserRepository.findActiveIds(userIds));
		return userId -> active.contains(userId) ? userId : null;
	}
}
