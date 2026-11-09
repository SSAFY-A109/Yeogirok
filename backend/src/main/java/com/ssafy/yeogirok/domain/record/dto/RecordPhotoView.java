package com.ssafy.yeogirok.domain.record.dto;

/**
 * 기록 사진(노션 059 RecordPhoto). uploadedBy 는 탈퇴했거나 없는 회원이면 null. attribution 은 기록 사진에 없어 항상 null.
 *
 * <p>TODO url: 기록 참여 권한을 확인한 만료 URL. 파일 조회 URL 발급({@code FileUrlIssuer})이 PR #64 로 backend/dev 에
 * 들어오면 추가한다(명세 {@code specs/record-api.md} 4-3).
 *
 * @param visitId 연결된 방문, 미연결이면 null
 */
public record RecordPhotoView(Long imageId, String attribution, Long uploadedBy, Long visitId) {
}
