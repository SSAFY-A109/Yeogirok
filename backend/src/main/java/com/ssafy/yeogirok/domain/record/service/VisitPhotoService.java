package com.ssafy.yeogirok.domain.record.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.dto.PhotoDeleteResponse;
import com.ssafy.yeogirok.domain.record.dto.PhotoLinkResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.ActiveUserRepository;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 방문에 사진 연결·이동(API-068)과 기록 사진 삭제(API-089). 사진 한 장은 한 방문에만 연결되고, 연결 해제(보관함으로 되돌리기)는 없다.
 * 연결·이동·삭제는 방문 삭제(067)와 같은 기록 행 잠금 아래에서 처리해, 경합 중에 연결 사진을 놓치지 않는다.
 * 업로드 완료(086, RecordPhotoService)는 미연결 사진을 보관함에 넣기만 해 잠금·version 대상이 아니다.
 *
 * <p>{@code UNIQUE (visit_id, position) DEFERRABLE INITIALLY IMMEDIATE}(Flyway V1)는 문장마다 검사하므로, 자리가 바뀌는
 * 사진을 먼저 겹치지 않는 임시 값으로 옮긴 뒤 최종 값으로 바꾼다({@link VisitLayout} 과 같은 이유).
 */
@Service
@RequiredArgsConstructor
public class VisitPhotoService {

	/** 임시 position. CHECK (position >= 1) 를 지키면서 실제 순서와 겹치지 않는 값. */
	private static final int TEMP_BASE = 1_000_000;

	private final RecordFinder recordFinder;
	private final TravelRecordRepository travelRecordRepository;
	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final ActiveUserRepository activeUserRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 기록 행을 잠근 뒤 쓰기 권한(RE001·RE003) → 이 기록의 방문(RE013) → 이 기록의 사진(없거나 다른 기록이면 M004,
	 * 다른 기록 사진의 존재를 드러내지 않는다. 명세 5절 11) 순으로 확인한다. 사진은 대상 방문 맨 뒤에 보낸 순서대로 붙고,
	 * 원래 방문의 남은 사진은 순서를 당긴다. 이미 이 방문에 있는 사진은 그대로 둔다. 실제로 옮긴 사진이 있을 때만
	 * 기록 version+1·DRAFT.
	 *
	 * <p>visit_images 행은 업로드 완료(086)에서 RECORD_PHOTO 로 확정된 사진만 생기므로 M006·M007 은 여기서 나지 않는다.
	 */
	@Transactional
	public PhotoLinkResponse link(Long userId, Long recordId, Long visitId, List<Long> imageIds) {
		TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
		if (!actualVisitRepository.existsByIdAndRecordId(visitId, recordId)) {
			throw new BusinessException(ExceptionType.VISIT_NOT_FOUND);
		}
		List<VisitImage> images = visitImageRepository.findByRecordIdOrderByIdAsc(recordId);
		Map<Long, VisitImage> byId = images.stream().collect(toMap(VisitImage::getId, Function.identity()));
		if (imageIds.stream().anyMatch(id -> !byId.containsKey(id))) {
			throw new BusinessException(ExceptionType.FILE_NOT_FOUND);
		}
		if (move(images, imageIds.stream().map(byId::get).toList(), visitId)) {
			record.markEdited(clock.instant());
		}
		return response(imageIds.stream().map(byId::get).toList(), record);
	}

	/** 같은 멱등키 재전송. 현재 쓰기 권한을 다시 확인하고 그 사진들의 지금 연결과 기록 상태를 준다(그사이 지운 사진은 뺀다). */
	@Transactional(readOnly = true)
	public PhotoLinkResponse getLinked(Long userId, Long recordId, List<Long> imageIds) {
		recordFinder.checkWritable(recordId, userId);
		List<VisitImage> images = visitImageRepository.findAllById(imageIds).stream()
			.filter(image -> image.getRecordId().equals(recordId))
			.sorted(Comparator.comparingInt(image -> imageIds.indexOf(image.getId())))
			.toList();
		return response(images, travelRecordRepository.findById(recordId).orElseThrow());
	}

	/**
	 * API-089. 업로더와 상관없이 현재 참여자 누구나 지운다. 기록 행을 잠근 뒤 권한(RE001·RE003) → version(C004) →
	 * 이 기록의 사진(M004) 순으로 확인하고, 행을 지운 뒤 같은 방문의 남은 사진 순서를 당기고 기록 version+1·DRAFT.
	 * 파일은 커밋 뒤 {@link RecordPhotoFileCleaner} 가 지운다. 이야기는 지우지 않는다.
	 */
	@Transactional
	public PhotoDeleteResponse delete(Long userId, Long recordId, Long imageId, int version) {
		TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
		if (record.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		VisitImage image = visitImageRepository.findById(imageId)
			.filter(found -> found.getRecordId().equals(recordId))
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		visitImageRepository.delete(image);
		visitImageRepository.flush();
		if (image.getVisitId() != null) {
			// 앞자리부터 하나씩 당기면 옮길 자리가 항상 비어 있어 순서 유니크(DEFERRABLE)와 겹치지 않는다.
			List<VisitImage> rest = visitImageRepository.findByVisitIdOrderByPositionAsc(image.getVisitId());
			for (int i = 0; i < rest.size(); i++) {
				if (rest.get(i).getPosition() != i + 1) {
					rest.get(i).moveTo(i + 1);
					visitImageRepository.flush();
				}
			}
		}
		record.markEdited(clock.instant());
		eventPublisher.publishEvent(new RecordPhotoFileCleaner.RecordPhotoDeleted(imageId, image.getImagePath()));
		return new PhotoDeleteResponse(recordId, imageId, true, record.getStatus(), record.getVersion());
	}

	/** 같은 멱등키 재전송. 현재 쓰기 권한을 다시 확인하고 처음과 같은 성공 결과(지금 기록 상태)를 준다. */
	@Transactional(readOnly = true)
	public PhotoDeleteResponse getDeleted(Long userId, Long recordId, Long imageId) {
		recordFinder.checkWritable(recordId, userId);
		TravelRecord record = travelRecordRepository.findById(recordId).orElseThrow();
		return new PhotoDeleteResponse(recordId, imageId, true, record.getStatus(), record.getVersion());
	}

	/** @return 옮긴 사진이 있으면 true */
	private boolean move(List<VisitImage> images, List<VisitImage> requested, Long visitId) {
		Map<Long, List<VisitImage>> byVisit = new HashMap<>(images.stream()
			.filter(image -> image.getVisitId() != null)
			.sorted(Comparator.comparing(VisitImage::getPosition))
			.collect(groupingBy(VisitImage::getVisitId)));
		Map<VisitImage, Integer> before = new IdentityHashMap<>();
		images.forEach(image -> before.put(image, image.getPosition()));
		List<VisitImage> target = byVisit.computeIfAbsent(visitId, id -> new ArrayList<>());
		List<VisitImage> moved = new ArrayList<>();
		for (VisitImage image : requested) {
			if (visitId.equals(image.getVisitId())) {
				continue;
			}
			if (image.getVisitId() != null) {
				byVisit.get(image.getVisitId()).remove(image);
			}
			target.add(image);
			moved.add(image);
		}
		if (moved.isEmpty()) {
			return false;
		}
		Set<VisitImage> movedSet = Collections.newSetFromMap(new IdentityHashMap<>());
		movedSet.addAll(moved);
		Map<VisitImage, Integer> finalPosition = new IdentityHashMap<>();
		Map<VisitImage, Long> finalVisit = new IdentityHashMap<>();
		byVisit.forEach((id, list) -> {
			for (int i = 0; i < list.size(); i++) {
				finalPosition.put(list.get(i), i + 1);
				finalVisit.put(list.get(i), id);
			}
		});
		List<VisitImage> changed = finalPosition.keySet().stream()
			.filter(image -> movedSet.contains(image) || !finalPosition.get(image).equals(before.get(image)))
			.toList();
		int temp = TEMP_BASE;
		for (VisitImage image : changed) {
			place(image, movedSet.contains(image), finalVisit.get(image), temp++);
		}
		visitImageRepository.flush();
		for (VisitImage image : changed) {
			place(image, movedSet.contains(image), finalVisit.get(image), finalPosition.get(image));
		}
		visitImageRepository.flush();
		return true;
	}

	private static void place(VisitImage image, boolean moved, Long visitId, int position) {
		if (moved) {
			image.linkTo(visitId, position);
		} else {
			image.moveTo(position);
		}
	}

	private PhotoLinkResponse response(List<VisitImage> images, TravelRecord record) {
		Set<Long> uploaders = new HashSet<>();
		images.forEach(image -> uploaders.add(image.getUploadedBy()));
		Set<Long> active = uploaders.isEmpty() ? Set.of() : new HashSet<>(activeUserRepository.findActiveIds(uploaders));
		return new PhotoLinkResponse(images.stream().map(image -> new PhotoLinkResponse.Photo(image.getId(),
			image.getVisitId(), active.contains(image.getUploadedBy()) ? image.getUploadedBy() : null)).toList(),
			record.getVersion(), record.getStatus());
	}
}
