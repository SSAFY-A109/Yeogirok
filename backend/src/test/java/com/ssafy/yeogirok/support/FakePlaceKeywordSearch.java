package com.ssafy.yeogirok.support;

import java.util.ArrayList;
import java.util.List;

import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch;

/** 카카오 키워드 검색 대신 쓴다. 정해 둔 결과를 돌려주고 받은 검색어를 남긴다. 기본은 결과 없음. */
public class FakePlaceKeywordSearch implements PlaceKeywordSearch {

	private final List<String> queries = new ArrayList<>();
	private Result result = Result.NOT_FOUND;
	private Runnable onSearch = () -> {
	};

	public void willReturn(Result next) {
		this.result = next;
	}

	/** 검색하는 순간 실행할 동작(예: OCR 슬롯이 비었는지 확인). */
	public void onSearch(Runnable action) {
		this.onSearch = action;
	}

	public List<String> queries() {
		return List.copyOf(queries);
	}

	public void reset() {
		queries.clear();
		result = Result.NOT_FOUND;
		onSearch = () -> {
		};
	}

	@Override
	public Result search(String query) {
		queries.add(query);
		onSearch.run();
		return result;
	}
}
