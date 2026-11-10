package com.ssafy.yeogirok.domain.record.dto;

/**
 * 기록 사진(노션 059 RecordPhoto). uploadedBy 는 탈퇴했거나 없는 회원이면 null. attribution 은 기록 사진에 없어 항상 null.
 *
 * @param url     기록 참여 권한을 확인한 뒤 발급한 만료 GET URL(만료는 API-013 과 같은 download-url-ttl). 저장하지 않는다
 * @param visitId 연결된 방문, 미연결이면 null
 */
public record RecordPhotoView(Long imageId, String url, String attribution, Long uploadedBy, Long visitId) {
}
