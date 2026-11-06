package com.ssafy.yeogirok.domain.file;

/**
 * 업로드 용도. 업로드 전에 앱이 고르고 서버가 자동으로 분류하지 않는다(API-012).
 * 영수증만 영수증 버킷에 올리고 나머지는 사진 버킷에 올린다.
 */
public enum FilePurpose {

	TIP_PHOTO("tip-photo/", false),
	RECORD_PHOTO("record-photo/", true),
	RECEIPT("receipt/", true);

	/** 검증을 마친 파일을 옮겨 두는 확정 경로. 임시 경로는 용도와 상관없이 {@code tmp/}. */
	private final String confirmedPrefix;
	/** recordId 가 필요한 용도. */
	private final boolean recordRequired;

	FilePurpose(String confirmedPrefix, boolean recordRequired) {
		this.confirmedPrefix = confirmedPrefix;
		this.recordRequired = recordRequired;
	}

	public String confirmedKey(String uploadId) {
		return confirmedPrefix + uploadId;
	}

	public boolean recordRequired() {
		return recordRequired;
	}

	public boolean isReceipt() {
		return this == RECEIPT;
	}
}
