package com.ssafy.yeogirok.domain.record.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkResponse;
import com.ssafy.yeogirok.domain.record.dto.MaterialListResponse;
import com.ssafy.yeogirok.domain.record.dto.MaterialView;
import com.ssafy.yeogirok.domain.record.material.MaterialLinkService;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.material.MaterialQueryService;
import com.ssafy.yeogirok.domain.record.dto.MaterialRetryResponse;
import com.ssafy.yeogirok.domain.record.material.MaterialRetryService;
import com.ssafy.yeogirok.domain.record.material.MaterialReviewService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 영수증 자료 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 서비스 테스트가 본다. */
@WebMvcTest(RecordMaterialController.class)
@Import(WebMvcSecurity.class)
class RecordMaterialControllerTest {

	static final Long USER_ID = 1L;
	static final String UPLOAD_ID = "0a1b2c3d-0000-4000-8000-000000000001";

	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	MaterialLinkService materialLinkService;
	@MockitoBean
	MaterialQueryService materialQueryService;
	@MockitoBean
	MaterialReviewService materialReviewService;
	@MockitoBean
	MaterialRetryService materialRetryService;
	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(asUser(USER_ID)).header("Idempotency-Key", "key-1")
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Nested
	class API_062 {

		static final String PATH = "/api/v1/records/10/materials";

		@Test
		void 연결_응답은_201() throws Exception {
			MaterialLinkCommand command = new MaterialLinkCommand(MaterialLinkCommand.Type.RECEIPT, UPLOAD_ID);
			given(materialLinkService.link(USER_ID, 10L, command))
				.willReturn(MaterialLinkResponse.queued("m-1", 10L, "job-1"));

			mockMvc.perform(json(post(PATH), "{\"type\":\"RECEIPT\",\"uploadId\":\"" + UPLOAD_ID + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success":true,"data":{"materialId":"m-1","recordId":10,"status":"UPLOADED","version":1,
					 "jobId":"job-1","ocrStatus":"QUEUED"}}
					""", JsonCompareMode.STRICT));
			verify(idempotencyService).execute(eq(new IdempotencyKey(USER_ID, RecordMaterialController.LINK_SCOPE,
				"10", "key-1")), eq(command), any(), any(), any());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"uploadId\":\"" + UPLOAD_ID + "\"}|C001",
			"{\"type\":null,\"uploadId\":\"" + UPLOAD_ID + "\"}|C001",
			"{\"type\":1,\"uploadId\":\"" + UPLOAD_ID + "\"}|C002",
			"{\"type\":\"PHOTO\",\"uploadId\":\"" + UPLOAD_ID + "\"}|C003",
			"{\"type\":\"receipt\",\"uploadId\":\"" + UPLOAD_ID + "\"}|C003",
			"{\"type\":\"RECEIPT\"}|C001",
			"{\"type\":\"RECEIPT\",\"uploadId\":null}|C001",
			"{\"type\":\"RECEIPT\",\"uploadId\":\"not-uuid\"}|C002",
			"{\"type\":\"RECEIPT\",\"uploadId\":3}|C002"
		})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post(PATH), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(materialLinkService);
		}

		@Test
		void 멱등키가_없으면_C001() throws Exception {
			mockMvc.perform(post(PATH).with(asUser(USER_ID)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"type\":\"RECEIPT\",\"uploadId\":\"" + UPLOAD_ID + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
		}
	}

	@Nested
	class API_091 {

		static final String MATERIAL_ID = "11111111-2222-4333-8444-555555555555";
		static final String PATH = "/api/v1/records/10/materials/" + MATERIAL_ID + "/review";
		static final String PLACE = "\"customPlace\":{\"name\":\" 안목 카페 \",\"latitude\":37.77,\"longitude\":128.947}";

		@Test
		void 보정_응답은_200_이고_paidAt_은_09_로_자른_날짜_시각이다() throws Exception {
			given(materialReviewService.review(eq(USER_ID), eq(10L), eq(MATERIAL_ID), any()))
				.willReturn(new MaterialReviewResponse(MATERIAL_ID, 3, ReceiptMaterial.ReviewStatus.CONFIRMED, false,
					List.of(), new MaterialView.Values("가게", Instant.parse("2026-11-01T15:30:15Z"), null,
						LocalDate.of(2026, 11, 2), LocalTime.of(0, 30, 15)), ReceiptMaterial.Status.READY, true));

			mockMvc.perform(json(patch(PATH), "{\"version\":2,\"merchantName\":\" 가게 \","
					+ "\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success":true,"data":{"materialId":"11111111-2222-4333-8444-555555555555","version":3,
					 "reviewStatus":"CONFIRMED","needsReview":false,"missingFields":[],
					 "values":{"merchantName":"가게","paidAt":"2026-11-02 00:30:15+09","place":null,
					   "paidDate":"2026-11-02","paidTime":"00:30:15"},
					 "status":"READY","canUseForDraft":true}}
					""", JsonCompareMode.STRICT));
			ArgumentCaptor<MaterialReviewCommand> command = ArgumentCaptor.forClass(MaterialReviewCommand.class);
			verify(materialReviewService).review(eq(USER_ID), eq(10L), eq(MATERIAL_ID), command.capture());
			assertThat(command.getValue().merchantName()).isEqualTo("가게");
			assertThat(command.getValue().paidAt().toLocalTime()).isEqualTo(LocalTime.of(0, 30, 15));
			assertThat(command.getValue().place()).isEqualTo(new VisitPlaceInput.CustomPlace("안목 카페",
				new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null));
			verify(idempotencyService).execute(eq(new IdempotencyKey(USER_ID, RecordMaterialController.REVIEW_SCOPE,
				"10-" + MATERIAL_ID, "key-1")), eq(command.getValue()), any(), any(), any());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}|C001",
			"{\"version\":null,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}|C001",
			"{\"version\":\"2\",\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}|C002",
			"{\"version\":2,\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}|C001",
			"{\"version\":2,\"merchantName\":\"  \",\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}|C003",
			"{\"version\":2,\"merchantName\":3,\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}|C002",
			"{\"version\":2,\"merchantName\":\"가게\"," + PLACE + "}|C001",
			"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15\"," + PLACE + "}|C002",
			"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-01 15:30:15Z\"," + PLACE + "}|C002",
			"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\"}|C001",
			"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\",\"placeRef\":"
				+ "{\"provider\":\"TOUR_API\",\"providerPlaceId\":\"T-1\"}," + PLACE + "}|C003",
			"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\",\"placeRef\":"
				+ "{\"provider\":\"KAKAO\",\"providerPlaceId\":\"T-1\"}}|C003",
			"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\",\"customPlace\":"
				+ "{\"name\":\"카페\",\"latitude\":91,\"longitude\":128}}|C011",
			"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\",\"customPlace\":"
				+ "{\"name\":\"  \",\"latitude\":37,\"longitude\":128}}|P004"
		})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(patch(PATH), body))
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(materialReviewService);
		}

		@Test
		void 자료_ID_가_UUID_가_아니면_C002() throws Exception {
			mockMvc.perform(json(patch("/api/v1/records/10/materials/not-uuid/review"),
					"{\"version\":2,\"merchantName\":\"가게\",\"paidAt\":\"2026-11-02 00:30:15+09\"," + PLACE + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C002"));
			verifyNoInteractions(materialReviewService);
		}
	}

	@Nested
	class API_092 {

		static final String MATERIAL_ID = "11111111-2222-4333-8444-555555555555";
		static final String PATH = "/api/v1/records/10/materials/" + MATERIAL_ID + "/ocr";

		@Test
		void 재시도_응답은_202() throws Exception {
			given(materialRetryService.retry(USER_ID, 10L, MATERIAL_ID, 2))
				.willReturn(MaterialRetryResponse.queued(MATERIAL_ID, 3, "job-2"));

			mockMvc.perform(json(post(PATH), "{\"version\":2}"))
				.andExpect(status().isAccepted())
				.andExpect(content().json("""
					{"success":true,"data":{"materialId":"11111111-2222-4333-8444-555555555555","version":3,
					 "jobId":"job-2","status":"QUEUED"}}
					""", JsonCompareMode.STRICT));
			verify(idempotencyService).execute(eq(new IdempotencyKey(USER_ID, RecordMaterialController.RETRY_SCOPE,
				"10-" + MATERIAL_ID, "key-1")), eq(2), any(), any(), any());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {"{}|C001", "{\"version\":null}|C001", "{\"version\":\"2\"}|C002",
			"{\"version\":1.5}|C002"})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post(PATH), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(materialRetryService);
		}

		@Test
		void 자료_ID_가_UUID_가_아니면_C002() throws Exception {
			mockMvc.perform(json(post("/api/v1/records/10/materials/not-uuid/ocr"), "{\"version\":2}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C002"));
		}
	}

	@Nested
	class API_063 {

		@Test
		void 목록은_items_이고_시각은_09_시각만_있는_값은_HH_mm_ss() throws Exception {
			Instant paidAt = Instant.parse("2026-11-02T03:00:00Z");
			MaterialView view = new MaterialView("m-1", "RECEIPT", ReceiptMaterial.Status.READY,
				new MaterialView.Extracted("가게", List.of(), paidAt, null, LocalDate.of(2026, 11, 2), LocalTime.NOON),
				true, null, 2, "job-1",
				new MaterialView.Values("가게", paidAt, null, LocalDate.of(2026, 11, 2), LocalTime.NOON),
				ReceiptMaterial.ReviewStatus.REQUIRED, List.of("place"), false, true,
				Instant.parse("2026-11-03T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"));
			given(materialQueryService.list(USER_ID, 10L)).willReturn(new MaterialListResponse(List.of(view)));

			mockMvc.perform(get("/api/v1/records/10/materials").with(asUser(USER_ID)))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success":true,"data":{"items":[{
					  "materialId":"m-1","type":"RECEIPT","status":"READY",
					  "extracted":{"merchantName":"가게","placeCandidates":[],"paidAt":"2026-11-02 12:00:00+09",
					    "confidence":null,"paidDate":"2026-11-02","paidTime":"12:00:00"},
					  "needsReview":true,"error":null,"version":2,"jobId":"job-1",
					  "values":{"merchantName":"가게","paidAt":"2026-11-02 12:00:00+09","place":null,
					    "paidDate":"2026-11-02","paidTime":"12:00:00"},
					  "reviewStatus":"REQUIRED","missingFields":["place"],"canUseForDraft":false,
					  "originalAvailable":true,"originalDeleteAfter":"2026-11-03 09:00:00+09",
					  "uploadAcceptedAt":"2026-11-02 09:00:00+09"}]}}
					""", JsonCompareMode.STRICT));
		}
	}
}
