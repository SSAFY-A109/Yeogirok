package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

/** API-064 검사를 마친 요청. 사진·영수증 각각 1개 이상, 중복 없음. */
public record DraftCreateCommand(int baseVersion, List<Long> imageIds, List<MaterialRef> materials) {

	public record MaterialRef(String materialId, int version) {
	}
}
