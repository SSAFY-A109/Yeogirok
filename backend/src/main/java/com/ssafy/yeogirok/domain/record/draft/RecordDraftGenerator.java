package com.ssafy.yeogirok.domain.record.draft;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftComposer.Photo;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 통합 기록 초안 생성 작업(RECORD_GENERATION, 명세 {@code specs/record-api-2.md} 4-7). 작업 스레드에서 돈다. DB 에는 쓰지 않고
 * 초안(Redis)만 바꾼다. 실패하면 초안을 FAILED 로 저장한 뒤 예외를 다시 던져 작업도 실패로 끝낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordDraftGenerator {

	/** EXIF 를 찾으려고 읽는 사진 앞부분(명세 5절 13). JPEG 는 거의 이 안에 있다. */
	static final int METADATA_READ_BYTES = 256 * 1024;

	private final RecordDraftStore draftStore;
	private final RecordAccess recordAccess;
	private final VisitImageRepository visitImageRepository;
	private final FileStorage fileStorage;
	private final FileProperties fileProperties;
	private final PhotoMetadataReader metadataReader;
	private final RecordDraftProperties properties;

	public Map<String, Object> generate(String draftKey) {
		try {
			RecordDraft draft = draftStore.find(draftKey)
				.orElseThrow(() -> new IllegalStateException("기록 초안이 없습니다. draftKey=" + draftKey));
			// 접수 뒤 방이 삭제됐거나 요청자가 나갔으면 만들지 않는다(5절 21, 다시 시도 불가).
			if (!recordAccess.isCurrentMember(draft.recordId(), draft.requestedBy())) {
				throw new BusinessException(ExceptionType.RECORD_EDIT_FORBIDDEN);
			}
			draftStore.save(draft.running());
			RecordDraftComposer.Result result = RecordDraftComposer.compose(draft.sourceSnapshot().materials(),
				photos(draft), properties.draft());
			draftStore.save(draft.succeeded(result.items(), result.warnings()));
			return Map.of("type", "RECORD_DRAFT", "draftKey", draftKey);
		} catch (RuntimeException e) {
			fail(draftKey, e instanceof BusinessException business
				? new JobError(business.getExceptionType().getCode(), business.getMessage())
				: new JobError(ExceptionType.SERVER_ERROR.getCode(), ExceptionType.SERVER_ERROR.getMessage()));
			throw e;
		}
	}

	/** 대기 제한 시간이 지나 실행하지 못했거나 실행기에 맡기지 못했다. */
	public void fail(String draftKey, JobError error) {
		draftStore.find(draftKey).ifPresent(draft -> draftStore.save(draft.failed(error)));
	}

	public void expire(String draftKey) {
		fail(draftKey, Job.TIMED_OUT);
	}

	/** 접수 뒤 지워졌거나 EXIF 를 읽지 못한 사진은 촬영 시각 없음으로 둔다(그 사진은 근거로 쓰지 않는다). */
	private List<Photo> photos(RecordDraft draft) {
		List<Long> imageIds = draft.sourceSnapshot().imageIds();
		Map<Long, VisitImage> images = visitImageRepository.findAllById(imageIds).stream()
			.filter(image -> image.getRecordId().equals(draft.recordId()))
			.collect(Collectors.toMap(VisitImage::getId, Function.identity()));
		return imageIds.stream()
			.map(id -> new Photo(id, Optional.ofNullable(images.get(id)).flatMap(this::metadata)))
			.toList();
	}

	private Optional<PhotoMetadataReader.PhotoMetadata> metadata(VisitImage image) {
		try {
			return metadataReader.read(fileStorage.readPrefix(fileProperties.photoBucket(), image.getImagePath(),
				METADATA_READ_BYTES));
		} catch (RuntimeException e) {
			log.warn("기록 사진을 읽지 못해 초안 근거에서 뺍니다. imageId={}, cause={}", image.getId(),
				e.getClass().getSimpleName());
			return Optional.empty();
		}
	}
}
