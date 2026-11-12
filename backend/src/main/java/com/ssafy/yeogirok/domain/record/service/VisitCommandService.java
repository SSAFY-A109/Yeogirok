package com.ssafy.yeogirok.domain.record.service;

import static java.util.stream.Collectors.toMap;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.draft.DraftApplication;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.RecordPlaceRepository;
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
	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final VisitStoryRepository visitStoryRepository;
	private final RecordPlaceRepository recordPlaceRepository;
	private final PlaceRepository placeRepository;
	private final LegalDongResolver legalDongResolver;
	private final DraftApplication draftApplication;
	private final ReferencePlans referencePlans;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public VisitCommandService(RecordFinder recordFinder, RecordVisitViews recordVisitViews,
		ActualVisitRepository actualVisitRepository,
		VisitImageRepository visitImageRepository, VisitStoryRepository visitStoryRepository,
		RecordPlaceRepository recordPlaceRepository, PlaceRepository placeRepository, LegalDongResolver legalDongResolver,
		DraftApplication draftApplication, ReferencePlans referencePlans, PlatformTransactionManager transactionManager,
		Clock clock) {
		this.recordFinder = recordFinder;
		this.recordVisitViews = recordVisitViews;
		this.actualVisitRepository = actualVisitRepository;
		this.visitImageRepository = visitImageRepository;
		this.visitStoryRepository = visitStoryRepository;
		this.recordPlaceRepository = recordPlaceRepository;
		this.placeRepository = placeRepository;
		this.legalDongResolver = legalDongResolver;
		this.draftApplication = draftApplication;
		this.referencePlans = referencePlans;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * API-066. 방문을 끼워 넣고 기록 version+1·DRAFT. 방문은 is_public_selected=TRUE 이고, 초안 항목(itemId)은
	 * creation_type=AI·출처(draftKey·itemId), 직접 추가는 USER 다. 초안 검사는 기록 행 잠금 안에서, 반영 표시는 커밋 뒤에 한다.
	 */
	public VisitAddResponse add(Long userId, Long recordId, VisitAddCommand command) {
		Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs = legalDongs(userId, recordId,
			command.items().stream().map(VisitAddCommand.Item::place));
		VisitAddResponse response = transactionTemplate.execute(status -> {
			TravelRecord record = lockAndCheckVersion(userId, recordId, command.version());
			if (command.fromDraft()) {
				draftApplication.check(recordId, command);
			}
			referencePlans.checkLinkable(record,
				command.items().stream().map(VisitAddCommand.Item::plannedItemId).toList());
			Instant now = clock.instant();
			VisitLayout layout = new VisitLayout(
				actualVisitRepository.findByRecordIdOrderByVisitDateAscPositionAsc(recordId));
			List<ActualVisit> added = new ArrayList<>();
			for (VisitAddCommand.Item item : command.items()) {
				Long placeId = placeId(item.place(), legalDongs, now);
				ActualVisit visit = item.itemId() == null
					? ActualVisit.manual(recordId, placeId, item.visitDate(), item.visitedAt(), now)
					: ActualVisit.fromDraft(recordId, placeId, item.visitDate(), item.visitedAt(), command.draftKey(),
						item.itemId(), now);
				visit.linkPlannedItem(item.plannedItemId(), now);
				layout.insert(visit, item.order());
				added.add(visit);
			}
			layout.apply(now, actualVisitRepository::flush, actualVisitRepository::save);
			record.markEdited(now);
			return new VisitAddResponse(added.stream().map(ActualVisit::getId).toList(), record.getVersion(),
				record.getStatus());
		});
		if (command.fromDraft()) {
			draftApplication.markApplied(command.draftKey());
		}
		return response;
	}

	/** 같은 멱등키 재전송. 현재 쓰기 권한을 다시 확인하고 그때 만든 방문 ID 와 지금 기록 상태를 준다. */
	public VisitAddResponse getAdded(Long userId, Long recordId, List<Long> visitIds) {
		TravelRecord record = recordFinder.checkWritable(recordId, userId);
		return new VisitAddResponse(visitIds, record.getVersion(), record.getStatus());
	}

	/**
	 * API-067. 수정·삭제 대상이 모두 이 기록의 방문인지(RE013) 먼저 본다. 삭제할 방문에 사진이 하나라도 있으면 RE020,
	 * 사진은 없고 이야기가 있는데 confirmDeleteStories 가 아니면 RE023 으로 요청 전체를 거부한다. 확인하면 이야기 → 방문 순서로
	 * 지우고(CASCADE 없음) 남은 순서를 다시 매긴다. 실제로 바뀐 것이 있을 때만 기록 version+1·DRAFT.
	 * 지우거나 장소를 바꿔 아무도 쓰지 않게 된 직접 입력(MANUAL) 장소 행은 같이 지운다.
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
			referencePlans.checkLinkable(record, command.items().stream()
				.filter(VisitUpdateCommand.Item::plannedItemGiven).map(VisitUpdateCommand.Item::plannedItemId).toList());
			VisitLayout layout = new VisitLayout(visits);
			Set<Long> released = new HashSet<>();
			boolean changed = remove(command, byId, layout, released);
			for (VisitUpdateCommand.Item item : command.items()) {
				ActualVisit visit = byId.get(item.visitId());
				Long placeId = placeIdFor(visit, item.place(), legalDongs, now);
				if (!placeId.equals(visit.getPlaceId())) {
					released.add(visit.getPlaceId());
				}
				boolean dateChanged = !visit.getVisitDate().equals(item.visitDate());
				changed |= visit.change(placeId, item.visitDate(), item.visitedAt(), now);
				if (item.plannedItemGiven()) {
					changed |= visit.linkPlannedItem(item.plannedItemId(), now);
				}
				if (item.order() != null || dateChanged) {
					layout.insert(visit, item.order());
				}
			}
			changed |= layout.apply(now, actualVisitRepository::flush, actualVisitRepository::save);
			if (changed) {
				record.markEdited(now);
			}
			actualVisitRepository.flush();
			deleteUnusedManualPlaces(released);
			return new VisitUpdateResponse(recordVisitViews.load(recordId).visits(), record.getVersion(),
				record.getStatus());
		});
	}

	/** @return 지운 방문이 있으면 true */
	private boolean remove(VisitUpdateCommand command, Map<Long, ActualVisit> byId, VisitLayout layout,
		Set<Long> released) {
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
			released.add(visit.getPlaceId());
			layout.remove(visit);
			actualVisitRepository.delete(visit);
		}
		// 남은 방문의 순서를 당기기 전에 지운 자리를 비운다.
		actualVisitRepository.flush();
		return true;
	}

	/**
	 * 067 의 새 장소. 생략이면 지금 장소. 지금 장소가 직접 입력이고 이름·좌표·주소가 같으면 새 행을 만들지 않고 그대로 쓴다
	 * (059 응답을 그대로 돌려보낸 요청이 version 을 올리고 완성을 풀지 않게, PR #77 리뷰).
	 */
	private Long placeIdFor(ActualVisit visit, VisitPlaceInput input,
		Map<VisitPlaceInput.CustomPlace, LegalDong> legalDongs, Instant now) {
		if (input == null) {
			return visit.getPlaceId();
		}
		if (input instanceof VisitPlaceInput.CustomPlace custom && placeRepository.findById(visit.getPlaceId())
			.filter(current -> sameManualPlace(current, custom)).isPresent()) {
			return visit.getPlaceId();
		}
		return placeId(input, legalDongs, now);
	}

	private static boolean sameManualPlace(Place place, VisitPlaceInput.CustomPlace custom) {
		return place.getProvider() == PlaceProvider.MANUAL && place.getName().equals(custom.name())
			&& place.getLatitude().compareTo(custom.latitude()) == 0
			&& place.getLongitude().compareTo(custom.longitude()) == 0
			&& Objects.equals(place.getAddress(), custom.address());
	}

	/**
	 * 방문 삭제·장소 교체로 놓인 장소 중, 직접 입력(MANUAL)이고 실제 방문·여행방 후보 어디에서도 가리키지 않는 행을 지운다.
	 * FK 가 없어 DB 가 막아 주지 않는다. 투어API 장소는 여러 곳이 같이 쓰는 행이라 지우지 않는다.
	 */
	private void deleteUnusedManualPlaces(Set<Long> placeIds) {
		for (Long placeId : placeIds) {
			placeRepository.findById(placeId)
				.filter(place -> place.getProvider() == PlaceProvider.MANUAL)
				.filter(place -> !recordPlaceRepository.isUsedByVisit(placeId)
					&& !recordPlaceRepository.isUsedByRoomPlace(placeId))
				.ifPresent(placeRepository::delete);
		}
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
			case VisitPlaceInput.TourPlace tour -> placeRepository
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
