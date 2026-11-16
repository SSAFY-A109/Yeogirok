package com.ssafy.yeogirok.domain.statistics;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.domain.footprint.repository.FootprintVisit;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 탈퇴 정리 중 발자취(노션 ERD › ERD 설계 관련 사항). 탈퇴 트랜잭션 안에서 회원 행을 잠근 채로 돈다. 발자취 작성·삭제도
 * 같은 회원 행 잠금({@link com.ssafy.yeogirok.global.lock.UserLock})을 쓰므로 그사이 새 발자취가 끼어들지 않는다.
 *
 * <p>미삭제 발자취를 footprint_statistics 에 합산해 방문 분포(API-046)에 남기고, 그 회원의 발자취 원본은 삭제 상태까지
 * 모두 지운다. 실패하면 탈퇴도 롤백된다(원본은 지웠는데 집계가 빠지는 일이 없게).
 */
@Component
@RequiredArgsConstructor
public class WithdrawnFootprints {

	/** 같은 셀·날짜·카테고리 집합끼리 모으고, 이 순서로 정렬해 쓴다({@link PreservedVisitCounts#add}). */
	private static final Comparator<Key> KEY_ORDER = Comparator.comparingLong(Key::latitudeIndex)
		.thenComparingLong(Key::longitudeIndex)
		.thenComparing(Key::statDate)
		.thenComparing(Key::categoryCodes);

	private final FootprintRepository footprintRepository;
	private final PreservedVisitCounts preservedVisitCounts;
	private final VisitStatisticsProperties properties;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onUserWithdrawn(UserWithdrawnEvent event) {
		preserveAndDelete(event.userId());
	}

	/**
	 * 탈퇴 처리와 {@code WithdrawalCleanupJob}(이 정리가 생기기 전에 탈퇴한 회원)이 같이 쓴다. 호출한 쪽이 회원 행을 잠근다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void preserveAndDelete(Long userId) {
		List<PreservedVisit> visits = aggregate(footprintRepository.findActiveVisits(userId));
		if (!visits.isEmpty()) {
			preservedVisitCounts.add(visits, clock.instant());
		}
		footprintRepository.deleteAllByUserId(userId);
	}

	/** 기본 셀(설정의 기본 셀 크기)·방문 날짜(Asia/Seoul)·정렬한 카테고리 집합별 발자취 수. */
	List<PreservedVisit> aggregate(List<FootprintVisit> footprints) {
		VisitGrid baseGrid = new VisitGrid(properties.baseCellSizeDegrees(), 1);
		Map<Key, Long> counts = new TreeMap<>(KEY_ORDER);
		for (FootprintVisit footprint : footprints) {
			Key key = new Key(baseGrid.cellIndex(footprint.latitude()), baseGrid.cellIndex(footprint.longitude()),
				LocalDate.ofInstant(footprint.visitedAt(), ApiDateTime.ZONE),
				categoryCodes(footprint.categoryCodes()));
			counts.merge(key, 1L, Long::sum);
		}
		return counts.entrySet().stream()
			.map(entry -> {
				Key key = entry.getKey();
				return new PreservedVisit(properties.aggregationVersion(),
					key.latitudeIndex() + ":" + key.longitudeIndex(), key.statDate(), key.categoryCodes(),
					baseGrid.center(key.latitudeIndex()), baseGrid.center(key.longitudeIndex()), entry.getValue());
			})
			.toList();
	}

	/** footprint_statistics 의 category_codes 는 중복 없이 정렬한 전체 선택 집합이다(발자취는 고른 순서대로 저장). */
	private String categoryCodes(List<String> codes) {
		return jsonMapper.writeValueAsString(codes.stream().distinct().sorted().toList());
	}

	private record Key(long latitudeIndex, long longitudeIndex, LocalDate statDate, String categoryCodes) {
	}
}
