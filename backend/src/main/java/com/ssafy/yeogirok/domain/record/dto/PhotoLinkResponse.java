package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/** API-068 응답. photos 는 요청한 사진(보낸 순서), status 는 저장 후 기록 상태. */
public record PhotoLinkResponse(List<Photo> photos, Integer version, RecordStatus status) {

	/** @param uploadedBy 탈퇴했거나 없는 회원이면 null */
	public record Photo(Long imageId, Long visitId, Long uploadedBy) {
	}
}
