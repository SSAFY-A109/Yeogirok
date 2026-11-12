package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.room.dto.RoomInvitationResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomJoinResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomChange;
import com.ssafy.yeogirok.domain.room.event.RoomChangedEvent;
import com.ssafy.yeogirok.domain.room.invite.RoomInviteProperties;
import com.ssafy.yeogirok.domain.room.invite.RoomInviteTokenCipher;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 여행방 초대 링크(API-024)와 링크로 참여(API-025). 링크는 방당 하나이고 만료·재발급이 없다. 승인 없이 바로 참여한다.
 * 둘 다 방 행을 잠가 링크 생성, 참여 인원 확인, 마지막 참여자 퇴장(API-087)을 방마다 한 줄로 세운다.
 */
@Service
@RequiredArgsConstructor
public class RoomInvitationService {

	/** 생성자를 포함한 현재 참여자 최대 인원. 고정 서비스 정책이다. */
	public static final int MAX_MEMBERS = 10;

	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final RoomFinder roomFinder;
	private final RoomInviteTokenCipher cipher;
	private final RoomInviteProperties properties;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/** API-024. 링크가 없으면 만들고(created=true), 있으면 같은 링크를 돌려준다. 방이 가득 차도 링크는 보여 준다. */
	@Transactional
	public RoomInvitationResponse getOrCreateInvitation(Long userId, Long roomId) {
		TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
		if (room.hasInviteToken()) {
			return toResponse(room, cipher.decrypt(room.getInviteTokenEncrypted()), false);
		}
		String token = cipher.newToken();
		room.assignInviteToken(cipher.encrypt(token), clock.instant());
		travelRoomRepository.flush();
		return toResponse(room, token, true);
	}

	/**
	 * API-025. 이미 현재 참여 중이면 그대로 성공한다(인원을 다시 세지 않는다). 새로 참여하거나 나갔다가 다시 들어오면
	 * 방 행을 잠근 채 현재 인원을 확인하고, 10명이면 RO007. 삭제된 방의 링크는 RO001.
	 */
	@Transactional
	public RoomJoinResponse accept(Long userId, String inviteToken) {
		Long roomId = findRoomIdByToken(inviteToken);
		TravelRoom room = travelRoomRepository.findByIdForUpdate(roomId)
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_INVITE_TOKEN_INVALID));
		// 잠금을 잡은 뒤에 본다. 마지막 참여자가 막 나가 삭제된 방에는 들어가지 못한다.
		if (room.isDeleted()) {
			throw new BusinessException(ExceptionType.ROOM_NOT_FOUND);
		}

		Optional<RoomMember> existing = roomMemberRepository.findById(new RoomMemberId(roomId, userId));
		if (existing.isPresent() && existing.get().isCurrent()) {
			return RoomJoinResponse.joined(roomId, existing.get().isCreator());
		}
		if (roomMemberRepository.countCurrentMembers(roomId) >= MAX_MEMBERS) {
			throw new BusinessException(ExceptionType.ROOM_FULL);
		}

		Instant now = clock.instant();
		RoomMember member;
		if (existing.isPresent()) {
			member = existing.get();
			member.rejoin(now);
		} else {
			member = roomMemberRepository.save(RoomMember.joined(roomId, userId, now));
		}
		roomMemberRepository.flush();
		// 참여자 목록이 바뀌었다(방 version 은 오르지 않는다). 커밋 뒤 방에 연결된 참여자에게 알린다.
		eventPublisher.publishEvent(RoomChangedEvent.of(roomId, userId, room.getVersion(), RoomChange.MEMBERS));
		return RoomJoinResponse.joined(roomId, member.isCreator());
	}

	/** 같은 멱등키로 다시 보낸 참여 요청. 그사이 나갔으면 RO002, 방이 삭제됐으면 RO001. */
	@Transactional(readOnly = true)
	public RoomJoinResponse getJoined(Long userId, Long roomId) {
		RoomMember member = roomFinder.getForMember(roomId, userId).member();
		return RoomJoinResponse.joined(roomId, member.isCreator());
	}

	/** 우리가 만든 토큰보다 긴 값은 암호화하지 않고 무효로 본다. */
	private Long findRoomIdByToken(String inviteToken) {
		if (inviteToken.isEmpty() || inviteToken.length() > RoomInviteTokenCipher.TOKEN_LENGTH) {
			throw new BusinessException(ExceptionType.ROOM_INVITE_TOKEN_INVALID);
		}
		return travelRoomRepository.findIdByInviteTokenEncrypted(cipher.encrypt(inviteToken))
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_INVITE_TOKEN_INVALID));
	}

	private RoomInvitationResponse toResponse(TravelRoom room, String token, boolean created) {
		return new RoomInvitationResponse(room.getId(), token, null, properties.urlBase() + "/" + token, created);
	}
}
