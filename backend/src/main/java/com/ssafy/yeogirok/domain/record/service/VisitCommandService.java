package com.ssafy.yeogirok.domain.record.service;

import static java.util.stream.Collectors.toMap;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.TourPlaceRepository;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitStoryRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.global.geo.LegalDongResolver;

/**
 * 실제 방문 추가·수정·삭제(API-066·067). 멱등키(066)는 컨트롤러가 이 밖에서 처리한다.
 *
 * <p>직접 입력 장소가 있으면 권한을 먼저 확인하고 법정동을 구한다(외부 호출이라 트랜잭션·잠금 밖, 실패해도 null).
 * 그다음 기록 행을 잠가 권한(RE001·RE003)과 version(C004)을 확인하고 한 트랜잭션에서 반영한다.
 */
@Service
public class VisitCommandService {

	private final RecordFinder recordFinder;
	private final RecordVisitViews recordVisitViews;
	private final TravelRecordRepository travelRecordRepository;
	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final VisitStoryRepository visitStoryRepository;
	private final TourPlaceRepository tourPlaceRepository;
	private final PlaceRepository placeRepository;
	private final LegalDongResolver legalDongResolver;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public VisitCommandService(RecordFinder recordFinder, RecordVisitViews recordVisitViews,
		TravelRecordRepository travelRecordRepository, ActualVisitRepository actualVisitRepository,
		VisitImageRepository visitImageRepository, VisitStoryRepository visitStoryRepository,
		TourPlaceRepository tourPlaceRepository, PlaceRepository placeRepository, LegalDongResolver legalDongResolver,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.recordFinder = recordFinder;
		this.recordVisitViews = recordVisitViews;
		this.travelRecordRepository = travelRecordRepository;
		this.actualVisitRepository = actualVisitRepository;
		this.visitImageRepository = visitImageRepository;
		this.visitStoryRepository = visitStoryRepository;
		this.tourPlaceRepository = tourPlaceRepository;
		this.placeRepository = placeRepository;
		this.legalDongResolver = legalDongResolver;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** API-066. 방문을 끼워 넣고 기록 version+1·DRAFT. 방문은 creation_type=USER, is_public_selected=TRUE 다. */
	public VisitAddResponse add(Long userId, Long recordId, VisitAddCommand command) {
		Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs = legalDongs(userId, recordId,
			command.items().stream().map(VisitAddCommand.Item::place));
		return transactionTemplate.execute(status -> {
			TravelRecord record = lockAndCheckVersion(userId, recordId, command.version());
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
	 * API-067. 수정·삭제 대상이 모두 이 기록의 방문인지(RE013) 먼저 본다. 삭제할 방문에 사진이 하나라도 있으면 RE020,
	 * 사진은 없고 이야기가 있는데 confirmDeleteStories 가 아니면 RE023 으로 요청 전체를 거부한다. 확인하면 이야기 → 방문 순서로
	 * 지우고(CASCADE 없음) 남은 순서를 다시 매긴다. 실제로 바뀐 것이 있을 때만 기록 version+1·DRAFT.
	 */
	public VisitUpdateResponse update(Long userId, Long recordId, VisitUpdateCommand command) {
		Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs = legalDongs(userId, recordId,
			command.items().stream().map(VisitUpdateCommand.Item::place));
		return transactionTemplate.execute(status -> {
			TravelRecord record = lockAndCheckVersion(userId, recordId, command.version());
			Instant now = clock.instant();
			List<ActualVisit> visits = actualVisitRepository.findByRecordIdOrderByVisitDateAscPositionAsc(recordId);
			Map<Long, ActualVisit> byId = visits.stream().collect(toMap(ActualVisit::getId, Function.identity()));
			if (Stream.concat(command.items().stream().map(VisitUpdateCommand.Item::visitId),
				command.removeVisitIds().stream()).anyMatch(id -> !byId.containsKey(id))) {
				throw new BusinessException(ExceptionType.VISIT_NOT_FOUND);
			}
			VisitLayout layout = new VisitLayout(visits);
			boolean changed = remove(command, byId, layout);
			for (VisitUpdateCommand.Item item : command.items()) {
				ActualVisit visit = byId.get(item.visitId());
				Long placeId = item.place() == null ? visit.getPlaceId() : placeId(item.place(), legalDongs, now);
				boolean dateChanged = !visit.getVisitDate().equals(item.visitDate());
				changed |= visit.change(placeId, item.visitDate(), item.visitedAt(), now);
				if (item.order() != null || dateChanged) {
					layout.insert(visit, item.order());
				}
			}
			changed |= layout.apply(now, actualVisitRepository::flush, actualVisitRepository::save);
			if (changed) {
				record.markEdited(now);
			}
			actualVisitRepository.flush();
			return new VisitUpdateResponse(recordVisitViews.load(recordId).visits(), record.getVersion(),
				record.getStatus());
		});
	}

	/** @return 지운 방문이 있으면 true */
	private boolean remove(VisitUpdateCommand command, Map<Long, ActualVisit> byId, VisitLayout layout) {
		List<Long> removeIds = command.removeVisitIds();
		if (removeIds.isEmpty()) {
			return false;
		}
		if (visitImageRepository.existsByVisitIdIn(removeIds)) {
			throw new BusinessException(ExceptionType.VISIT_HAS_PHOTOS);
		}
		if (!command.confirmDeleteStories() && visitStoryRepository.existsByVisitIdIn(removeIds)) {
			throw new BusinessException(ExceptionType.VISIT_STORIES_NOT_CONFIRMED);
		}
		visitStoryRepository.deleteByVisitIdIn(removeIds);
		for (Long id : removeIds) {
			ActualVisit visit = byId.remove(id);
			layout.remove(visit);
			actualVisitRepository.delete(visit);
		}
		// 남은 방문의 순서를 당기기 전에 지운 자리를 비운다.
		actualVisitRepository.flush();
		return true;
	}

	private TravelRecord lockAndCheckVersion(Long userId, Long recordId, int version) {
		TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
		if (record.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		return record;
	}

	/** 직접 입력 장소가 있을 때만 권한을 먼저 확인하고(참여자가 아니면 카카오를 부르지 않는다) 장소마다 법정동을 구한다. */
	private Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs(Long userId, Long recordId,
		Stream<VisitPlaceInput> places) {
		List<VisitPlaceInput.CustomPlace> customs = places
			.filter(VisitPlaceInput.CustomPlace.class::isInstance).map(VisitPlaceInput.CustomPlace.class::cast).toList();
		Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs = new HashMap<>();
		if (customs.isEmpty()) {
			return legalDongs;
		}
		recordFinder.checkWritable(recordId, userId);
		customs.forEach(custom -> legalDongs.computeIfAbsent(custom,
			key -> legalDongResolver.resolve(key.latitude(), key.longitude())));
		return legalDongs;
	}

	/**
	 * 투어API 장소는 places 에 이미 있는 행만 쓴다(명세 5절 6). 직접 입력은 새 MANUAL 행을 만든다. 기존 장소 행은 고치지 않고
	 * 방문의 place_id 만 바꾼다(명세 1-7).
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
