package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.nonNullString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.request.Uuids;

import tools.jackson.databind.JsonNode;

/**
 * API-064 요청(노션 064). 누락·null C001, 타입 C002, 빈 배열·중복 imageId·중복 materialId C003. 개수 상한은 노션에 없어 068·066 과
 * 같은 값을 둔다(사진 100, 영수증 50, 넘으면 C003).
 */
public class DraftCreateRequest {

	static final int MAX_IMAGES = 100;
	static final int MAX_MATERIALS = 50;

	@JsonProperty("baseVersion")
	private JsonNode baseVersion;
	@JsonProperty("imageIds")
	private JsonNode imageIds;
	@JsonProperty("materials")
	private JsonNode materials;

	public DraftCreateCommand toCommand() {
		int version = VisitRequestFields.requiredInt(baseVersion, "baseVersion");
		VisitRequestFields.requiredArray(imageIds, "imageIds", false, MAX_IMAGES);
		VisitRequestFields.requiredArray(materials, "materials", false, MAX_MATERIALS);
		List<Long> images = new ArrayList<>();
		for (JsonNode id : imageIds) {
			long imageId = VisitRequestFields.requiredId(id, "imageIds");
			if (images.contains(imageId)) {
				throw VisitRequestFields.notAllowed("imageIds: 같은 사진이 두 번 있습니다(" + imageId + ").");
			}
			images.add(imageId);
		}
		List<DraftCreateCommand.MaterialRef> refs = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (JsonNode material : materials) {
			JsonNode object = VisitRequestFields.requiredObject(material, "materials[]");
			String materialId = Uuids.require(nonNullString(object.get("materialId"), "materials[].materialId"),
				"materials[].materialId");
			if (!seen.add(materialId)) {
				throw VisitRequestFields.notAllowed("materials: 같은 자료가 두 번 있습니다(" + materialId + ").");
			}
			refs.add(new DraftCreateCommand.MaterialRef(materialId,
				VisitRequestFields.requiredInt(object.get("version"), "materials[].version")));
		}
		return new DraftCreateCommand(version, images, refs);
	}
}
