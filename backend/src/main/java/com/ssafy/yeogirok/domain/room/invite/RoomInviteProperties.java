package com.ssafy.yeogirok.domain.room.invite;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * 여행방 초대 링크 설정.
 *
 * @param tokenKey 초대 토큰 암호화 키. Base64, 디코드했을 때 64바이트(AES-SIV 512비트 키). 환경 변수로만 넣는다
 * @param urlBase  inviteUrl 앞부분. 뒤에 {@code /{inviteToken}} 을 붙인다
 */
@Validated
@ConfigurationProperties("yeogirok.room.invite")
public record RoomInviteProperties(@NotBlank String tokenKey, @NotBlank String urlBase) {
}
