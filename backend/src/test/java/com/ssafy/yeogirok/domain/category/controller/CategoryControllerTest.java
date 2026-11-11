package com.ssafy.yeogirok.domain.category.controller;

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

import com.ssafy.yeogirok.domain.category.dto.CategoryListResponse;
import com.ssafy.yeogirok.domain.category.service.CategoryService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** API-079 HTTP 계약. */
@WebMvcTest(CategoryController.class)
@Import(WebMvcSecurity.class)
class CategoryControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	CategoryService service;

	@Test
	void 카테고리를_code와_name으로_돌려준다() throws Exception {
		given(service.getCategories()).willReturn(new CategoryListResponse(List.of(
			new CategoryListResponse.Category("NATURE", "자연 힐링"),
			new CategoryListResponse.Category("CULTURE", "역사 문화"))));

		mockMvc.perform(get("/api/v1/categories").with(asUser(1L)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "categories": [
				      { "code": "NATURE", "name": "자연 힐링" },
				      { "code": "CULTURE", "name": "역사 문화" }
				    ]
				  }
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 토큰이_없으면_A001() throws Exception {
		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}
}
