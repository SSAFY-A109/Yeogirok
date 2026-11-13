package com.ssafy.yeogirok.domain.place.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.place.dto.PlaceSearchResponse;
import com.ssafy.yeogirok.domain.place.tour.TourApiClient;
import com.ssafy.yeogirok.domain.place.tour.TourApiException;
import com.ssafy.yeogirok.domain.place.tour.TourKeywordQuery;
import com.ssafy.yeogirok.domain.place.tour.TourSearchPage;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.PageSize;
import com.ssafy.yeogirok.global.request.RegionCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 여행지 검색(API-026). TourAPI 키워드 검색을 그대로 한 쪽씩 넘겨준다. DB 에는 아무것도 저장하지 않는다(담을 때 저장한다).
 *
 * <ul>
 *   <li>q: 필수. 앞뒤 공백을 빼고 1~{@value #QUERY_MAX_LENGTH}자가 아니면 P002</li>
 *   <li>regionCode: 선택. 법정동 시·도 2자리 또는 시·군·구 5자리 숫자(코스 검색 API-014 와 같은 단위)가 아니면 P002</li>
 *   <li>cursor: base64url("{다음 쪽 번호}:{쪽 크기}"). 해석할 수 없거나 지금 limit 과 쪽 크기가 다르면 C006</li>
 *   <li>limit: 기본 20, 1~50 밖이면 C007</li>
 *   <li>TourAPI 가 검색 조건을 파라미터 오류로 거절하면 P002, 그 밖의 실패(키 없음 포함)는 P003</li>
 * </ul>
 * 좌표가 없는 항목은 담을 수 없어 빼므로 한 쪽이 limit 보다 적을 수 있다. totalCount 는 TourAPI 값 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

	static final int QUERY_MAX_LENGTH = 100;

	private final TourApiClient tourApiClient;

	public PlaceSearchResponse search(String q, String regionCode, String cursor, Integer limit) {
		String keyword = keyword(q);
		RegionCode.check(regionCode, ExceptionType.PLACE_SEARCH_CONDITION_INVALID);
		int size = PageSize.resolve(limit);
		int pageNo = decodePage(cursor, size);

		TourSearchPage page;
		try {
			// TODO 지역 목록(API-078)을 정할 때 시·도 코드를 TourAPI 기준과 맞춘다. TourAPI 는 전남·광주를 lDongRegnCd 12 로
			//  주고 예전 코드(46·29)로는 0건이다. 078 이 예전 코드를 쓰면 여기서 바꿔 넘긴다(추천 PlaceRecommendationService 도 같다).
			page = tourApiClient.searchKeyword(new TourKeywordQuery(keyword,
				regionCode == null ? null : regionCode.substring(0, 2),
				regionCode == null || regionCode.length() == 2 ? null : regionCode.substring(2),
				pageNo, size));
		} catch (TourApiException e) {
			if (e.isInvalidRequest()) {
				// 검색 조건(특수문자 섞인 검색어 등)을 TourAPI 가 받지 않았다. 다시 시도해도 같으므로 장애(P003)가 아니라 조건 오류다.
				throw new BusinessException(ExceptionType.PLACE_SEARCH_CONDITION_INVALID,
					"q·regionCode: 장소 정보 공급자가 받지 않는 검색 조건입니다.");
			}
			log.warn("장소 검색 중 TourAPI 를 쓰지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
		}
		boolean hasNext = (long) pageNo * size < page.totalCount();
		return new PlaceSearchResponse(page.places().stream().map(PlaceSearchResponse.Item::from).toList(),
			hasNext ? encodePage(pageNo + 1, size) : null, page.totalCount());
	}

	private static String keyword(String q) {
		String keyword = q == null ? "" : q.strip();
		if (keyword.isEmpty() || keyword.length() > QUERY_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.PLACE_SEARCH_CONDITION_INVALID,
				"q: 앞뒤 공백을 빼고 1~%d자여야 합니다.".formatted(QUERY_MAX_LENGTH));
		}
		return keyword;
	}

	static String encodePage(int pageNo, int size) {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString((pageNo + ":" + size).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 없으면 첫 쪽(1). TourAPI 는 쪽 번호×쪽 크기로 자르므로, 커서를 만든 쪽 크기와 지금 limit 이 다르면 결과가 빠지거나 겹친다.
	 * 그래서 쪽 크기가 다른 커서는 C006 이다.
	 */
	private static int decodePage(String cursor, int size) {
		if (cursor == null) {
			return 1;
		}
		try {
			String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
			if (parts.length != 2) {
				throw new BusinessException(ExceptionType.INVALID_CURSOR);
			}
			int pageNo = Integer.parseInt(parts[0]);
			if (pageNo < 2 || Integer.parseInt(parts[1]) != size) {
				throw new BusinessException(ExceptionType.INVALID_CURSOR);
			}
			return pageNo;
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ExceptionType.INVALID_CURSOR);
		}
	}
}
