package com.ssafy.yeogirok.domain.course.dto;

/**
 * 공개 코스 작성자(노션 016 PublicAuthor). 닉네임·이메일은 넣지 않는다.
 *
 * @param profileImageUrl 소셜 로그인에서 받은 외부 주소 그대로, 없으면 null(명세 course-publish-api.md 5절 4)
 */
public record PublicAuthor(Long id, String profileImageUrl) {
}
