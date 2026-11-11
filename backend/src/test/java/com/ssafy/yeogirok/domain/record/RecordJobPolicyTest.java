package com.ssafy.yeogirok.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.record.draft.RecordGenerationJobPolicy;
import com.ssafy.yeogirok.domain.record.material.ReceiptOcrJobPolicy;
import com.ssafy.yeogirok.domain.record.material.ReceiptOcrRunner;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * API-011 기록 작업 조회 권한(명세 specs/record-api-2.md 4-2). 참여자 판정 자체는 RecordAccessTest 가 본다.
 * OCR 은 업로더 본인(J002) → 현재 참여자(J003), 초안은 현재 참여자면 누구나.
 */
class RecordJobPolicyTest {

	static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
	static final Long RECORD_ID = 100L;
	static final Long REQUESTER = 7L;
	static final Long OTHER_MEMBER = 8L;

	RecordAccess recordAccess;
	ReceiptOcrJobPolicy ocrPolicy;
	RecordGenerationJobPolicy generationPolicy;

	@BeforeEach
	void setUp() {
		recordAccess = mock(RecordAccess.class);
		when(recordAccess.isCurrentMember(RECORD_ID, REQUESTER)).thenReturn(true);
		when(recordAccess.isCurrentMember(RECORD_ID, OTHER_MEMBER)).thenReturn(true);
		ocrPolicy = new ReceiptOcrJobPolicy(recordAccess);
		generationPolicy = new RecordGenerationJobPolicy(recordAccess);
	}

	static Job job(String type) {
		return Job.queued("job-1", type, REQUESTER, RECORD_ID.toString(), NOW, Duration.ofMinutes(10));
	}

	@Test
	void OCR_작업은_업로더_본인이_현재_참여자일_때_본다() {
		assertThatCode(() -> ocrPolicy.check(REQUESTER, job(ReceiptOcrRunner.TYPE))).doesNotThrowAnyException();
	}

	@Test
	void OCR_작업은_다른_참여자가_보면_J002() {
		assertCode(() -> ocrPolicy.check(OTHER_MEMBER, job(ReceiptOcrRunner.TYPE)), ExceptionType.JOB_NOT_REQUESTER);
	}

	@Test
	void OCR_작업은_업로더가_퇴장했거나_방이_삭제됐으면_J003() {
		when(recordAccess.isCurrentMember(RECORD_ID, REQUESTER)).thenReturn(false);

		assertCode(() -> ocrPolicy.check(REQUESTER, job(ReceiptOcrRunner.TYPE)), ExceptionType.JOB_RESOURCE_FORBIDDEN);
	}

	@Test
	void 초안_작업은_요청자가_아니어도_현재_참여자면_본다() {
		assertThatCode(() -> generationPolicy.check(OTHER_MEMBER, job(RecordGenerationJobPolicy.TYPE)))
			.doesNotThrowAnyException();
	}

	@Test
	void 초안_작업은_퇴장했거나_방이_삭제됐으면_요청자라도_J003() {
		when(recordAccess.isCurrentMember(RECORD_ID, REQUESTER)).thenReturn(false);

		assertCode(() -> generationPolicy.check(REQUESTER, job(RecordGenerationJobPolicy.TYPE)),
			ExceptionType.JOB_RESOURCE_FORBIDDEN);
	}

	static void assertCode(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getExceptionType()).isEqualTo(type));
	}
}
