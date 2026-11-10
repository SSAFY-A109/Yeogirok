package com.ssafy.yeogirok.domain.record.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.TourPlaceRepository;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.global.geo.LegalDongResolver;

/** 실제 방문 추가(API-066). 멱등키는 컨트롤러가 이 밖에서 처리한다. */
@Service
public class VisitCommandService {

	private final RecordFinder recordFinder;
	private final TravelRecordRepository travelRecordRepository;
	private final ActualVisitRepository actualVisitRepository;
	private final TourPlaceRepository tourPlaceRepository;
	private final PlaceRepository placeRepository;
	private final LegalDongResolver legalDongResolver;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public VisitCommandService(RecordFinder recordFinder, TravelRecordRepository travelRecordRepository,
		ActualVisitRepository actualVisitRepository, TourPlaceRepository tourPlaceRepository,
		PlaceRepository placeRepository, LegalDongResolver legalDongResolver,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.recordFinder = recordFinder;
		this.travelRecordRepository = travelRecordRepository;
		this.actualVisitRepository = actualVisitRepository;
		this.tourPlaceRepository = tourPlaceRepository;
		this.placeRepository = placeRepository;
		this.legalDongResolver = legalDongResolver;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * API-066. 직접 입력 장소가 있으면 권한을 먼저 확인하고 법정동을 구한다(외부 호출이라 트랜잭션·잠금 밖, 실패해도 null).
	 * 그다음 기록 행을 잠가 권한(RE001·RE003) → version(C004) → 장소 확인 → 방문 끼워 넣기 → 기록 version+1·DRAFT 를
	 * 한 트랜잭션에서 한다. 방문은 creation_type=USER, is_public_selected=TRUE 다.
	 */
	public VisitAddResponse add(Long userId, Long recordId, VisitAddCommand command) {
		Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs = new HashMap<>();
		if (command.items().stream().anyMatch(item -> item.place() instanceof VisitPlaceInput.CustomPlace)) {
			recordFinder.checkWritable(recordId, userId);
			for (VisitAddCommand.Item item : command.items()) {
				if (item.place() instanceof VisitPlaceInput.CustomPlace custom) {
					legalDongs.computeIfAbsent(custom,
						key -> legalDongResolver.resolve(key.latitude(), key.longitude()));
				}
			}
		}
		return transactionTemplate.execute(status -> {
			TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
			if (record.getVersion() != command.version()) {
				throw new BusinessException(ExceptionType.VERSION_MISMATCH);
			}
			Instant now = clock.instant();
			VisitLayout layout = new VisitLayout(
				actualVisitRepository.findByRecordIdOrderByVisitDateAscPositionAsc(recordId));
			List<ActualVisit> added = new ArrayList<>();
			for (VisitAddCommand.Item item : command.items()) {
				ActualVisit visit = ActualVisit.manual(recordId, placeId(item.place(), legalDongs, now),
					item.visitDate(), item.visitedAt(), now);
				layout.insert(visit, item.order());
				added.add(visit);
			}
			layout.apply(now, actualVisitRepository::flush, actualVisitRepository::save);
			record.markEdited(now);
			return new VisitAddResponse(added.stream().map(ActualVisit::getId).toList(), record.getVersion(),
				record.getStatus());
		});
	}

	/** 같은 멱등키 재전송. 현재 쓰기 권한을 다시 확인하고 그때 만든 방문 ID 와 지금 기록 상태를 준다. */
	public VisitAddResponse getAdded(Long userId, Long recordId, List<Long> visitIds) {
		recordFinder.checkWritable(recordId, userId);
		TravelRecord record = travelRecordRepository.findById(recordId).orElseThrow();
		return new VisitAddResponse(visitIds, record.getVersion(), record.getStatus());
	}

	/**
	 * 투어API 장소는 places 에 이미 있는 행만 쓴다(명세 5절 6). 직접 입력은 새 MANUAL 행을 만든다.
	 * TODO 투어API 장소 조회(API-026·027) 클라이언트가 생기면 places 에 없는 장소를 조회·저장하고, 없는 장소는 P001,
	 *  연동 실패는 P003 으로 바꾼다.
	 */
	private Long placeId(VisitPlaceInput input, Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs, Instant now) {
		return switch (input) {
			case VisitPlaceInput.TourPlace tour -> tourPlaceRepository
				.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, tour.providerPlaceId())
				.orElseThrow(() -> new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
					"placeRef: 아직 저장되지 않은 투어API 장소입니다."))
				.getId();
			case VisitPlaceInput.CustomPlace custom -> {
				LegalDong legalDong = legalDongs.get(custom);
				yield placeRepository.save(Place.manual(custom.name(), custom.latitude(), custom.longitude(),
					custom.address(), legalDong.code(), legalDong.name(), now)).getId();
			}
		};
	}
}
