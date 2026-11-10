package com.ssafy.yeogirok.domain.job;

/** 실패한 작업의 공개 가능한 오류. code 는 API 오류 코드와 같은 체계다. */
public record JobError(String code, String message) {
}
