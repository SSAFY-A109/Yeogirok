package com.ssafy.yeogirok.domain.place.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.place.dto.PlaceSearchResponse;
import com.ssafy.yeogirok.domain.place.tour.TourApiClient;
import com.ssafy.yeogirok.domain.place.tour.TourApiException;
import com.ssafy.yeogirok.domain.place.tour.TourKeywordQuery;
import com.ssafy.yeogirok.domain.place.tour.TourSearchPage;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.PageSize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 여행지 검색(API-026). TourAPI 키워드 검색을 그대로 한 쪽씩 넘겨준다. DB 에는 아무것도 저장하지 않는다(담을 때 저장한다).
 *
 * <ul>
 *   <li>q: 필수. 앞뒤 공백을 빼고 1~{@value #QUERY_MAX_LENGTH}자가 아니면 P002</li>
 *   <li>regionCode: 선택. 법정동 시·도 2자리 또는 시·군·구 5자리 숫자(코스 검색 API-014 와 같은 단위)가 아니면 P002</li>
 *   <li>cursor: base64url("{다음 쪽 번호}"). 해석할 수 없으면 C006. 쪽 크기는 limit 로 정하므로 같은 limit 로 이어 받는다</li>
 *   <li>limit: 기본 20, 1~50 밖이면 C007</li>
 *   <li>TourAPI 실패(키 없음 포함)는 P003</li>
 * </ul>
 * 좌표가 없는 항목은 담을 수 없어 빼므로 한 쪽이 limit 보다 적을 수 있다. totalCount 는 TourAPI 값 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

	static final int QUERY_MAX_LENGTH = 100;
	private static final Pattern REGION_CODE = Pattern.compile("\\d{2}|\\d{5}");

	private final TourApiClient tourApiClient;

	public PlaceSearchResponse search(String q, String regionCode, String cursor, Integer limit) {
		String keyword = keyword(q);
		checkRegionCode(regionCode);
		int size = PageSize.resolve(limit);
		int pageNo = decodePage(cursor);

		TourSearchPage page;
		try {
			page = tourApiClient.searchKeyword(new TourKeywordQuery(keyword,
				regionCode == null ? null : regionCode.substring(0, 2),
				regionCode == null || regionCode.length() == 2 ? null : regionCode.substring(2),
				pageNo, size));
		} catch (TourApiException e) {
			log.warn("장소 검색 중 TourAPI 를 쓰지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
		}
		boolean hasNext = (long) pageNo * size < page.totalCount();
		return new PlaceSearchResponse(page.places().stream().map(PlaceSearchResponse.Item::from).toList(),
			hasNext ? encodePage(pageNo + 1) : null, page.totalCount());
	}

	private static String keyword(String q) {
		String keyword = q == null ? "" : q.strip();
		if (keyword.isEmpty() || keyword.length() > QUERY_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.PLACE_SEARCH_CONDITION_INVALID,
				"q: 앞뒤 공백을 빼고 1~%d자여야 합니다.".formatted(QUERY_MAX_LENGTH));
		}
		return keyword;
	}

	private static void checkRegionCode(String regionCode) {
		if (regionCode != null && !REGION_CODE.matcher(regionCode).matches()) {
			throw new BusinessException(ExceptionType.PLACE_SEARCH_CONDITION_INVALID,
				"regionCode: 시·도 2자리 또는 시·군·구 5자리 숫자여야 합니다.");
		}
	}

	static String encodePage(int pageNo) {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(Integer.toString(pageNo).getBytes(StandardCharsets.UTF_8));
	}

	/** 없으면 첫 쪽(1). */
	private static int decodePage(String cursor) {
		if (cursor == null) {
			return 1;
		}
		try {
			int pageNo = Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
			if (pageNo < 2) {
				throw new BusinessException(ExceptionType.INVALID_CURSOR);
			}
			return pageNo;
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ExceptionType.INVALID_CURSOR);
		}
	}
}
