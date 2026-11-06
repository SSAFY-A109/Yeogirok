package com.ssafy.yeogirok.domain.file.session;

/** UPLOADING 은 아직 쓸 수 없는 파일, READY 는 검증·확정을 마친 파일, FAILED 는 검증 실패(새 세션으로 다시 올린다). */
public enum UploadStatus {
	UPLOADING,
	READY,
	FAILED
}
