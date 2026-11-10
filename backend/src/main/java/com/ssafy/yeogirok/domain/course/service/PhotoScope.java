package com.ssafy.yeogirok.domain.course.service;

/** 공개 코스 응답에 넣을 사진. */
public enum PhotoScope {
	/** 방문에 연결된 사진 전부. 게시하면 공개될 사진이다(API-072 includePhotos=true). */
	LINKED,
	/** 공개 선택된 사진만. 게시된 코스 조회(016)용이다. */
	PUBLIC_SELECTED,
	/** 사진 없음(API-072 includePhotos=false). */
	NONE
}
