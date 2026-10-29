package com.ssafy.yeogirok.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger UI: /swagger-ui.html. 우상단 Authorize 에 서비스 액세스 토큰을 넣으면 보호 API 를 호출할 수 있다. */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("여기록 API").version("v1"))
			.components(new Components().addSecuritySchemes("bearerAuth",
				new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}
}
