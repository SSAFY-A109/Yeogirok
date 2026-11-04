package com.ssafy.yeogirok.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 {@code Long} 파라미터에 현재 로그인한 사용자 ID 를 넣는다({@link CurrentUserIdArgumentResolver}). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
