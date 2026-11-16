package com.ssafy.yeogirok.domain.region.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.yeogirok.domain.region.dto.RegionListResponse;
import com.ssafy.yeogirok.domain.region.entity.RegionLevel;
import com.ssafy.yeogirok.domain.region.service.RegionService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** API-078 HTTP 계약. */
@WebMvcTest(RegionController.class)
@Import(WebMvcSecurity.class)
class RegionControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RegionService service;

	@Test
	void parentCode가_없으면_시도_목록을_parentCode_null로_돌려준다() throws Exception {
		given(service.getRegions(null)).willReturn(new RegionListResponse(List.of(
			new RegionListResponse.Item("11", "서울특별시", RegionLevel.PROVINCE, null, true))));

		mockMvc.perform(get("/api/v1/regions").with(asUser(1L)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "items": [
				      { "code": "11", "name": "서울특별시", "level": "PROVINCE", "parentCode": null, "hasChildren": true }
				    ]
				  }
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void parentCode를_서비스에_그대로_넘긴다() throws Exception {
		given(service.getRegions("51")).willReturn(new RegionListResponse(List.of(
			new RegionListResponse.Item("51150", "강릉시", RegionLevel.DISTRICT, "51", false))));

		mockMvc.perform(get("/api/v1/regions").param("parentCode", "51").with(asUser(1L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].code").value("51150"))
			.andExpect(jsonPath("$.data.items[0].level").value("DISTRICT"))
			.andExpect(jsonPath("$.data.items[0].parentCode").value("51"))
			.andExpect(jsonPath("$.data.items[0].hasChildren").value(false));
	}

	@Test
	void 잘못된_parentCode는_C003() throws Exception {
		given(service.getRegions("99")).willThrow(new BusinessException(ExceptionType.NOT_ALLOWED_VALUE));

		mockMvc.perform(get("/api/v1/regions").param("parentCode", "99").with(asUser(1L)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C003"));
	}

	@Test
	void 토큰이_없으면_A001() throws Exception {
		mockMvc.perform(get("/api/v1/regions"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}
}
