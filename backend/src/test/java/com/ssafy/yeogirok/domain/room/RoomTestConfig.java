package com.ssafy.yeogirok.domain.room;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.room.invite.RoomInviteProperties;
import com.ssafy.yeogirok.domain.room.invite.RoomInviteTokenCipher;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;

/** 여행방 서비스 @DataJpaTest 용 빈. */
@TestConfiguration
@Import({RoomService.class, RoomInvitationService.class, RoomPlaceService.class, RoomFinder.class,
	CategoryNames.class})
public class RoomTestConfig {

	/** 직접 추가 후보의 기본 법정동. */
	public static final LegalDong LEGAL_DONG = new LegalDong("4215010100", "강릉동");

	@Bean
	FixedLegalDongResolver legalDongResolver() {
		return new FixedLegalDongResolver(LEGAL_DONG);
	}

	/** 2026-11-05 09:00 Asia/Seoul. */
	public static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

	/** application-test.yaml 과 같은 테스트 전용 키(Base64, 64바이트). */
	public static final String INVITE_TOKEN_KEY =
		"dGVzdC1vbmx5LXJvb20taW52aXRlLWtleS1mb3IteWVvZ2lyb2stdGVzdHMtMDEyMzQ1Njc4OWFiY2RlZmdoaQ==";
	public static final String INVITE_URL_BASE = "https://test.yeogirok/invite";

	public static RoomInviteProperties inviteProperties() {
		return new RoomInviteProperties(INVITE_TOKEN_KEY, INVITE_URL_BASE);
	}

	@Bean
	MutableClock clock() {
		return new MutableClock(NOW);
	}

	@Bean
	RoomInviteProperties roomInviteProperties() {
		return inviteProperties();
	}

	@Bean
	RoomInviteTokenCipher roomInviteTokenCipher(RoomInviteProperties properties) {
		return new RoomInviteTokenCipher(properties);
	}
}
