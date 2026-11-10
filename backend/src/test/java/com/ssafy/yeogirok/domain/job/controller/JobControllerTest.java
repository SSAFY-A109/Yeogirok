package com.ssafy.yeogirok.domain.job.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.dto.JobResponse;
import com.ssafy.yeogirok.domain.job.service.JobService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** API-011 HTTP 계약. 권한·상태 규칙은 JobServiceTest 가 본다. */
@WebMvcTest(JobController.class)
@Import(WebMvcSecurity.class)
class JobControllerTest {

	static final Long USER_ID = 1L;
	static final String JOB_ID = "550e8400-e29b-41d4-a716-446655440000";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	JobService jobService;

	@Test
	void 성공한_작업의_상태와_결과를_돌려준다() throws Exception {
		given(jobService.get(USER_ID, JOB_ID)).willReturn(new JobResponse(JOB_ID, "SCHEDULE_DRAFT",
			JobStatus.SUCCEEDED, Map.of("type", "SCHEDULE_DRAFT", "draftKey", "550e8400-e29b-41d4-a716-446655440001"),
			null, false));

		mockMvc.perform(get("/api/v1/jobs/" + JOB_ID).with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {
				  "jobId": "550e8400-e29b-41d4-a716-446655440000", "type": "SCHEDULE_DRAFT", "status": "SUCCEEDED",
				  "result": {"type": "SCHEDULE_DRAFT", "draftKey": "550e8400-e29b-41d4-a716-446655440001"},
				  "error": null, "retryable": false}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 실패한_작업은_오류를_돌려준다() throws Exception {
		given(jobService.get(USER_ID, JOB_ID)).willReturn(new JobResponse(JOB_ID, "SCHEDULE_DRAFT", JobStatus.FAILED,
			null, new JobError("SC002", "조건을 만족할 수 없습니다."), false));

		mockMvc.perform(get("/api/v1/jobs/" + JOB_ID).with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.result").isEmpty())
			.andExpect(jsonPath("$.data.error.code").value("SC002"));
	}

	@Test
	void 남의_작업은_J002() throws Exception {
		given(jobService.get(USER_ID, JOB_ID)).willThrow(new BusinessException(ExceptionType.JOB_NOT_REQUESTER));

		mockMvc.perform(get("/api/v1/jobs/" + JOB_ID).with(asUser(USER_ID)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("J002"));
	}
}
