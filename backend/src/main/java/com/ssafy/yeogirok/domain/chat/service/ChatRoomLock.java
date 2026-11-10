package com.ssafy.yeogirok.domain.chat.service;

/**
 * 같은 방의 메시지 저장(ID 발급·INSERT·커밋)을 한 줄로 세우는 채팅 전용 잠금. 트랜잭션이 끝나면 풀린다.
 *
 * <p>여행방 행 잠금(일정 저장·후보 변경·참여·나가기가 쓰는 것)과 따로 둔다(11/10 결정). 그래서 일정 저장 중에도
 * 메시지 전송이 기다리지 않는다. 방 안에서 ID 가 작은 메시지가 늦게 커밋되지 않게 해, 재연결 복구(afterMessageId)가
 * 메시지를 건너뛰지 않는다.
 */
public interface ChatRoomLock {

	/** 호출한 트랜잭션 안에서 부른다. */
	void lock(Long roomId);
}
