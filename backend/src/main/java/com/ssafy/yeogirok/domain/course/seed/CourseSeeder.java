package com.ssafy.yeogirok.domain.course.seed;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.service.CoursePreviewService;
import com.ssafy.yeogirok.domain.course.service.CoursePublishService;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.service.RecordCompleteService;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.StoryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.record.service.VisitPhotoService;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import lombok.extern.slf4j.Slf4j;

/**
 * 공식 코스 1개 넣기·내리기(명세 {@code specs/tour-course-seed.md} 6-4·6-6). 사진은 트랜잭션 밖에서 먼저 올리고, 방·기록·방문·이야기·
 * 사진 행은 한 트랜잭션으로 만든다. 실패하면 트랜잭션을 되돌리고 올린 파일을 지운다. 후보 장소(room_places)는 만들지 않는다.
 * 로그에는 contentId 만 남긴다.
 */
@Slf4j
@Component
class CourseSeeder {

	static final int TITLE_MAX = TravelRecord.TITLE_MAX_LENGTH;
	static final int DESCRIPTION_MAX = 2000;
	static final String SOURCE_NOTE = "출처: 한국관광공사(투어API)";
	static final int VISITS_PER_REQUEST = 50;

	enum Result { INSERTED, SKIPPED, FAILED }

	/** @param reason 실패 사유(짧은 코드). 성공·건너뜀이면 null */
	record Outcome(Result result, String reason) {

		static final Outcome INSERTED = new Outcome(Result.INSERTED, null);
		static final Outcome SKIPPED = new Outcome(Result.SKIPPED, null);

		static Outcome failed(String reason) {
			return new Outcome(Result.FAILED, reason);
		}
	}

	private final SeedRoomRepository seedRooms;
	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final TravelRecordRepository travelRecordRepository;
	private final VisitImageRepository visitImageRepository;
	private final SeedPlaces seedPlaces;
	private final RecordCreateService recordCreateService;
	private final VisitCommandService visitCommandService;
	private final StoryService storyService;
	private final VisitPhotoService visitPhotoService;
	private final RecordCompleteService recordCompleteService;
	private final CoursePreviewService coursePreviewService;
	private final CoursePublishService coursePublishService;
	private final SeedPhotos seedPhotos;
	private final TransactionTemplate transaction;
	private final Clock clock;

	CourseSeeder(SeedRoomRepository seedRooms, TravelRoomRepository travelRoomRepository,
		RoomMemberRepository roomMemberRepository, TravelRecordRepository travelRecordRepository,
		VisitImageRepository visitImageRepository, SeedPlaces seedPlaces, RecordCreateService recordCreateService,
		VisitCommandService visitCommandService, StoryService storyService, VisitPhotoService visitPhotoService,
		RecordCompleteService recordCompleteService, CoursePreviewService coursePreviewService,
		CoursePublishService coursePublishService, SeedPhotos seedPhotos,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.seedRooms = seedRooms;
		this.travelRoomRepository = travelRoomRepository;
		this.roomMemberRepository = roomMemberRepository;
		this.travelRecordRepository = travelRecordRepository;
		this.visitImageRepository = visitImageRepository;
		this.seedPlaces = seedPlaces;
		this.recordCreateService = recordCreateService;
		this.visitCommandService = visitCommandService;
		this.storyService = storyService;
		this.visitPhotoService = visitPhotoService;
		this.recordCompleteService = recordCompleteService;
		this.coursePreviewService = coursePreviewService;
		this.coursePublishService = coursePublishService;
		this.seedPhotos = seedPhotos;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** 내린 방까지 포함해 한 번이라도 넣은 코스인지. 내린 코스가 다음 실행에서 다시 들어가지 않게 한다. */
	boolean insertedEver(String contentId) {
		return seedRooms.existsEverByTitlePrefix(SeedRoomRepository.coursePattern(contentId));
	}

	/** 이 묶음으로 이미 넣은 코스인지(다시 넣기를 같은 묶음으로 반복하지 않게, 명세 6-6). */
	boolean insertedFrom(String contentId, String bundleId) {
		String title = SeedRoomRepository.titleOf(contentId, bundleId);
		return seedRooms.findActiveByTitlePrefix(SeedRoomRepository.coursePattern(contentId)).stream()
			.anyMatch(room -> room.getTitle().equals(title));
	}

	/** 넣은 적 없는 코스만 넣는다(이미 있거나 내린 코스면 SKIPPED — 내린 코스를 되돌리려면 다시 넣기). */
	Outcome seed(String bundleId, SeedBundle.Course course, Long ownerId, CourseSeedSource source) {
		if (insertedEver(course.contentId())) {
			return Outcome.SKIPPED;
		}
		return insert(bundleId, course, ownerId, source);
	}

	/** 지금 방을 내리고 새로 넣는다. 새로 넣기가 실패하면 내린 것도 되돌린다(한 트랜잭션). */
	Outcome reseed(String bundleId, SeedBundle.Course course, Long ownerId, CourseSeedSource source) {
		if (insertedFrom(course.contentId(), bundleId)) {
			return Outcome.SKIPPED;
		}
		return insert(bundleId, course, ownerId, source);
	}

	/** @return 내린 방 수. 이미 내렸거나 없으면 0(오류 아님) */
	int unpublish(List<String> contentIds, boolean all) {
		return transaction.execute(status -> {
			List<TravelRoom> rooms = all ? seedRooms.findActiveByTitlePrefix(SeedRoomRepository.allPattern())
				: contentIds.stream()
					.flatMap(id -> seedRooms.findActiveByTitlePrefix(SeedRoomRepository.coursePattern(id)).stream())
					.toList();
			Instant now = clock.instant();
			rooms.forEach(room -> room.softDelete(now));
			return rooms.size();
		});
	}

	private Outcome insert(String bundleId, SeedBundle.Course course, Long ownerId, CourseSeedSource source) {
		String problem = SeedBundleCheck.problem(course);
		if (problem != null) {
			return Outcome.failed(problem);
		}
		Map<Integer, SeedPhotos.Photo> photos = seedPhotos.read(course.contentId(), sorted(course.places()), source);
		boolean everyDayHasPhoto = course.places().stream().map(SeedBundle.Place::day).distinct()
			.allMatch(day -> photos.values().stream().anyMatch(photo -> photo.day() == day));
		if (!everyDayHasPhoto) {
			return Outcome.failed("PHOTO_MISSING_DAY");
		}
		List<String> uploaded = new ArrayList<>();
		try {
			seedPhotos.upload(photos.values(), uploaded);
			transaction.executeWithoutResult(status -> write(bundleId, course, ownerId, photos));
			return Outcome.INSERTED;
		} catch (RuntimeException e) {
			log.warn("공식 코스 넣기 실패. contentId={}, reason={}", course.contentId(), e.getClass().getSimpleName());
			seedPhotos.deleteQuietly(course.contentId(), uploaded);
			return Outcome.failed(e.getClass().getSimpleName());
		}
	}

	/** 한 트랜잭션 안. 다시 넣기면 지금 방을 먼저 내린다. */
	private void write(String bundleId, SeedBundle.Course course, Long ownerId, Map<Integer, SeedPhotos.Photo> photos) {
		Instant now = clock.instant();
		seedRooms.findActiveByTitlePrefix(SeedRoomRepository.coursePattern(course.contentId()))
			.forEach(room -> room.softDelete(now));
		List<SeedBundle.Place> places = sorted(course.places());
		LocalDate first = places.getFirst().visitDate();
		LocalDate last = places.getLast().visitDate();
		// RoomService.create 는 과거 시작일을 거부해 직접 저장한다(명세 6-4 우회).
		TravelRoom room = travelRoomRepository.save(TravelRoom.builder().title(SeedRoomRepository.titleOf(
				course.contentId(), bundleId)).startDate(first).endDate(last).transportMode(TransportMode.CAR)
			.createdAt(now).build());
		roomMemberRepository.save(RoomMember.creator(room.getId(), ownerId, now));
		places.forEach(seedPlaces::ensure);

		String title = cut(course.title().strip(), TITLE_MAX);
		Long recordId = recordCreateService.create(ownerId, room.getId(), title).recordId();
		int version = travelRecordRepository.findById(recordId).orElseThrow().getVersion();
		List<Long> visitIds = new ArrayList<>();
		for (int from = 0; from < places.size(); from += VISITS_PER_REQUEST) {
			List<VisitAddCommand.Item> items = places.subList(from, Math.min(from + VISITS_PER_REQUEST, places.size()))
				.stream()
				.map(place -> new VisitAddCommand.Item(new VisitPlaceInput.TourPlace(place.contentId()),
					place.visitDate(), visitedAt(place), place.order()))
				.toList();
			VisitAddResponse added = visitCommandService.add(ownerId, recordId, new VisitAddCommand(version, items));
			version = added.version();
			visitIds.addAll(added.visitIds());
		}
		for (int i = 0; i < places.size(); i++) {
			String story = places.get(i).overview();
			if (story != null && !story.isBlank()) {
				storyService.write(ownerId, recordId, visitIds.get(i), story.strip());
			}
			SeedPhotos.Photo photo = photos.get(i);
			if (photo != null) {
				Long imageId = visitImageRepository.save(VisitImage.unlinked(recordId, ownerId, photo.key(), now)).getId();
				visitPhotoService.link(ownerId, recordId, visitIds.get(i), List.of(imageId));
			}
		}
		version = recordCompleteService.complete(ownerId, recordId, currentVersion(recordId)).version();
		String description = description(course.overview());
		String hash = coursePreviewService.preview(ownerId, recordId,
			new CourseInput(version, title, description, true, null, null)).selectionHash();
		coursePublishService.publish(ownerId, recordId, new CourseInput(version, title, description, true, hash, null));
	}

	private int currentVersion(Long recordId) {
		return travelRecordRepository.findById(recordId).orElseThrow().getVersion();
	}

	static List<SeedBundle.Place> sorted(List<SeedBundle.Place> places) {
		return places.stream().sorted(Comparator.comparing(SeedBundle.Place::day)
			.thenComparing(SeedBundle.Place::order)).toList();
	}

	static Instant visitedAt(SeedBundle.Place place) {
		return LocalDateTime.of(place.visitDate(), LocalTime.parse(place.visitTime())).atZone(ApiDateTime.ZONE)
			.toInstant();
	}

	/** 코스 소개 + 출처 문구. 2,000자를 넘으면 소개를 줄인다. */
	static String description(String overview) {
		String body = overview == null ? "" : overview.strip();
		String suffix = body.isEmpty() ? SOURCE_NOTE : "\n\n" + SOURCE_NOTE;
		return cut(body, DESCRIPTION_MAX - suffix.codePointCount(0, suffix.length())) + suffix;
	}

	static String cut(String text, int maxCodePoints) {
		return text.codePointCount(0, text.length()) <= maxCodePoints ? text
			: text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
	}
}
