package com.ssafy.yeogirok.domain.tip.publish;

/**
 * 049 커밋 뒤 게시 작업 요청.
 * @param imagePath 연결할 사진 확정 경로(tip-photo/{uploadId}). 사진이 없으면 null
 */
public record TipPublishRequested(Long tipId, String jobId, String imagePath) {
}
