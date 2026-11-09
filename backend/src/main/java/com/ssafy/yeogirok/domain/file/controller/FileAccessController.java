package com.ssafy.yeogirok.domain.file.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.file.dto.FileAccessResponse;
import com.ssafy.yeogirok.domain.file.service.FileAccessService;
import com.ssafy.yeogirok.global.request.Uuids;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** API-013 파일 조회 URL. 팁 사진 경로(/tips/{tipId}/images/{imageId}/access)는 TipController 에 있다. */
@RestController
@RequestMapping("/api/v1/records/{recordId}")
@RequiredArgsConstructor
public class FileAccessController {

	private final FileAccessService fileAccessService;

	@Operation(summary = "API-013 기록 사진 조회 URL")
	@GetMapping("/images/{imageId}/access")
	public ResponseEntity<ApiResponseBody<FileAccessResponse>> recordPhoto(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long recordId, @PathVariable Long imageId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			fileAccessService.recordPhoto(user.userId(), recordId, imageId)));
	}

	@Operation(summary = "API-013 영수증 원본 조회 URL(업로더 본인)")
	@GetMapping("/materials/{materialId}/access")
	public ResponseEntity<ApiResponseBody<FileAccessResponse>> receipt(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long recordId, @PathVariable String materialId) {
		Uuids.require(materialId, "materialId");
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			fileAccessService.receipt(user.userId(), recordId, materialId)));
	}
}
