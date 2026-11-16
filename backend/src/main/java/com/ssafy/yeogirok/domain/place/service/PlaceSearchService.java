package com.ssafy.yeogirok.domain.place.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.place.dto.PlaceSearchResponse;
import com.ssafy.yeogirok.domain.place.tour.TourApiClient;
import com.ssafy.yeogirok.domain.place.tour.TourApiException;
import com.ssafy.yeogirok.domain.place.tour.TourKeywordQuery;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.domain.place.tour.TourRegionCodes;
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
 * 화성시 일반구처럼 옛 시 코드도 부르는 곳은 두 결과를 쪽 단위로 잇고 totalCount 는 둘의 합이다({@link #joined}).
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

		Result result;
		try {
			// regionCode 는 지역 목록(API-078)의 법정동 코드라 TourAPI lDongRegnCd·lDongSignguCd 와 같은 체계다(전남광주 12).
			String sido = TourRegionCodes.sido(regionCode);
			List<String> sigungus = TourRegionCodes.sigungus(regionCode);
			result = sigungus.size() <= 1
				? single(keyword, sido, sigungus.isEmpty() ? null : sigungus.getFirst(), pageNo, size)
				: joined(keyword, sido, sigungus.get(0), sigungus.get(1), pageNo, size);
		} catch (TourApiException e) {
			if (e.isInvalidRequest()) {
				// 검색 조건(특수문자 섞인 검색어 등)을 TourAPI 가 받지 않았다. 다시 시도해도 같으므로 장애(P003)가 아니라 조건 오류다.
				throw new BusinessException(ExceptionType.PLACE_SEARCH_CONDITION_INVALID,
					"q·regionCode: 장소 정보 공급자가 받지 않는 검색 조건입니다.");
			}
			log.warn("장소 검색 중 TourAPI 를 쓰지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
		}
		return new PlaceSearchResponse(result.places().stream().map(PlaceSearchResponse.Item::from).toList(),
			result.hasNext() ? encodePage(pageNo + 1, size) : null, result.totalCount());
	}

	/** 응답 한 쪽. totalCount 는 부른 TourAPI 결과 수의 합이다. */
	private record Result(List<TourPlace> places, int totalCount, boolean hasNext) {
	}

	private Result single(String keyword, String sido, String sigungu, int pageNo, int size) {
		TourSearchPage page = tourApiClient.searchKeyword(new TourKeywordQuery(keyword, sido, sigungu, pageNo, size));
		return new Result(page.places(), page.totalCount(), (long) pageNo * size < page.totalCount());
	}

	/**
	 * 고른 시·군·구 결과 쪽들 뒤에 옛 시 코드(화성시 590, {@link TourRegionCodes}) 결과 쪽들을 잇는다. TourAPI 는 좌표 없는 항목을
	 * 빼고 돌려줘 두 결과를 항목 단위로 잘라 붙일 수 없어 쪽 단위로 잇는다. 그래서 고른 시·군·구의 마지막 쪽은 limit 보다 적을 수
	 * 있다. 쪽마다 TourAPI 를 두 번 부른다(고른 시·군·구 쪽으로 그 결과 수를, 옛 코드 쪽으로 나머지를).
	 */
	private Result joined(String keyword, String sido, String selected, String legacy, int pageNo, int size) {
		TourSearchPage first = tourApiClient.searchKeyword(new TourKeywordQuery(keyword, sido, selected, pageNo, size));
		int firstPages = pages(first.totalCount(), size);
		boolean inFirst = pageNo <= firstPages;
		// 고른 시·군·구 쪽이면 옛 코드는 결과 수만 필요해 한 건만 받는다.
		TourSearchPage second = tourApiClient.searchKeyword(inFirst
			? new TourKeywordQuery(keyword, sido, legacy, 1, 1)
			: new TourKeywordQuery(keyword, sido, legacy, pageNo - firstPages, size));
		int lastPage = firstPages + pages(second.totalCount(), size);
		return new Result(inFirst ? first.places() : second.places(), first.totalCount() + second.totalCount(),
			pageNo < lastPage);
	}

	private static int pages(int totalCount, int size) {
		return (totalCount + size - 1) / size;
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
