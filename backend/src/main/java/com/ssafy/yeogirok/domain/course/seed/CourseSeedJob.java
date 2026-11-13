package com.ssafy.yeogirok.domain.course.seed;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.global.lock.RedisLock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공식 코스 적재 한 번(명세 {@code specs/tour-course-seed.md} 6-2): 내리기 → 다시 넣기 → 묶음 파일의 새 코스 넣기.
 * Redis 잠금으로 한 번에 하나만 돈다. 결과는 contentId 단위로 로그에 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CourseSeedJob {

	static final String LOCK_KEY = "course-seed:lock";
	static final Duration LOCK_TTL = Duration.ofMinutes(30);
	static final long BUNDLE_MAX_BYTES = 20L * 1024 * 1024;

	/**
	 * @param failed           contentId → 사유
	 * @param commandSkipped   같은 내리기·다시 넣기 설정을 이미 처리해 이번엔 건너뛰었는지
	 */
	record Report(int inserted, int skipped, Map<String, String> failed, int unpublished, int reseeded,
		boolean commandSkipped) {

		static final Report NOTHING = new Report(0, 0, Map.of(), 0, 0, false);
	}

	private record Loaded(String bundleId, SeedBundle.Course course) {
	}

	private final RedisLock redisLock;
	private final SeedOwner seedOwner;
	private final CourseSeeder seeder;
	private final FileStorage fileStorage;
	private final FileProperties fileProperties;
	private final JsonMapper jsonMapper;

	Report run(CourseSeedProperties properties) {
		if (properties.location() == null) {
			log.warn("공식 코스 적재 위치(yeogirok.course-seed.location)가 없어 아무것도 하지 않습니다.");
			return Report.NOTHING;
		}
		Optional<String> token = redisLock.tryAcquire(LOCK_KEY, LOCK_TTL);
		if (token.isEmpty()) {
			log.info("공식 코스 적재가 이미 돌고 있어 건너뜁니다.");
			return Report.NOTHING;
		}
		try {
			Optional<Long> owner = seedOwner.resolve(properties.ownerUserId());
			return owner.map(id -> runAs(id, properties)).orElse(Report.NOTHING);
		} finally {
			redisLock.release(LOCK_KEY, token.get());
		}
	}

	private Report runAs(Long ownerId, CourseSeedProperties properties) {
		CourseSeedSource source = CourseSeedSource.of(properties, fileStorage, fileProperties);
		List<Loaded> courses = load(source);
		// 끄는 걸 잊어 배포마다 다시 돌아도, 같은 내리기·다시 넣기 설정은 한 번만 처리한다(UNPUBLISH=ALL 이 나중 코스를
		// 내리거나, 같은 코스를 내렸다 다시 넣기를 반복하지 않게). 새 코스 넣기는 원래 두 번 돌아도 결과가 같다.
		String marker = CourseSeedSource.DONE_DIR + commandHash(ownerId, properties) + ".json";
		boolean hasCommand = !properties.unpublish().isEmpty() || !properties.reseed().isEmpty();
		boolean commandSkipped = hasCommand && alreadyApplied(source, marker);
		List<String> reseedIds = commandSkipped ? List.of() : properties.reseed();
		boolean doUnpublish = hasCommand && !commandSkipped && !properties.unpublish().isEmpty();
		int unpublished = doUnpublish ? seeder.unpublish(properties.unpublish(), properties.unpublishAll()) : 0;
		int inserted = 0;
		int skipped = 0;
		int reseeded = 0;
		Map<String, String> failed = new LinkedHashMap<>();
		for (String contentId : reseedIds) {
			Optional<Loaded> latest = courses.stream().filter(c -> c.course().contentId().equals(contentId))
				.reduce((first, second) -> second);
			if (latest.isEmpty()) {
				failed.put(contentId, "NOT_IN_BUNDLE");
				continue;
			}
			CourseSeeder.Outcome outcome = seeder.reseed(latest.get().bundleId(), latest.get().course(), ownerId, source);
			switch (outcome.result()) {
				case INSERTED -> reseeded++;
				case SKIPPED -> skipped++;
				case FAILED -> failed.put(contentId, outcome.reason());
			}
		}
		for (Loaded loaded : courses) {
			String contentId = loaded.course().contentId();
			if (reseedIds.contains(contentId) || failed.containsKey(contentId)) {
				continue;
			}
			CourseSeeder.Outcome outcome = seeder.seed(loaded.bundleId(), loaded.course(), ownerId, source);
			switch (outcome.result()) {
				case INSERTED -> inserted++;
				case SKIPPED -> skipped++;
				case FAILED -> failed.put(contentId, outcome.reason());
			}
		}
		if (hasCommand && !commandSkipped) {
			markApplied(source, marker, properties, unpublished, reseeded, failed);
		}
		Report report = new Report(inserted, skipped, failed, unpublished, reseeded, commandSkipped);
		log.info("공식 코스 적재 끝. 넣음={}, 건너뜀={}, 실패={}, 내림={}, 다시 넣음={}, 내리기·다시 넣기 이미 처리={}, 실패 사유={}",
			inserted, skipped, failed.size(), unpublished, reseeded, commandSkipped, failed);
		return report;
	}

	/** 내리기·다시 넣기 목록(정렬)과 주인으로 만든 짧은 해시. 값이 하나라도 바뀌면 다시 처리한다. */
	static String commandHash(Long ownerId, CourseSeedProperties properties) {
		String text = "owner=" + ownerId + ";unpublish=" + properties.unpublish().stream().sorted().toList()
			+ ";reseed=" + properties.reseed().stream().sorted().toList();
		return SeedPhotos.sha256(text.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
	}

	private boolean alreadyApplied(CourseSeedSource source, String marker) {
		if (!source.exists(marker)) {
			return false;
		}
		log.info("같은 내리기·다시 넣기 설정은 이미 처리해 건너뜁니다(새 코스 넣기만 합니다). 끝났으면 설정을 비워 주세요. marker={}", marker);
		return true;
	}

	/** 표시를 못 남기면 경고만(다음 실행에서 다시 처리될 수 있다). */
	private void markApplied(CourseSeedSource source, String marker, CourseSeedProperties properties, int unpublished,
		int reseeded, Map<String, String> failed) {
		Map<String, Object> content = new LinkedHashMap<>();
		content.put("unpublish", properties.unpublish());
		content.put("reseed", properties.reseed());
		content.put("unpublished", unpublished);
		content.put("reseeded", reseeded);
		content.put("failed", failed);
		try {
			source.write(marker, jsonMapper.writeValueAsBytes(content));
		} catch (RuntimeException e) {
			log.warn("내리기·다시 넣기 처리 표시를 남기지 못했습니다. marker={}, reason={}", marker, e.getClass().getSimpleName());
		}
	}

	/** 묶음 파일 이름순. 같은 코스가 여러 묶음에 있으면 앞 묶음 것으로 넣는다(다시 넣기는 마지막 묶음). */
	private List<Loaded> load(CourseSeedSource source) {
		List<Loaded> courses = new ArrayList<>();
		for (String name : source.bundles()) {
			try {
				SeedBundle bundle = jsonMapper.readValue(source.read(name, BUNDLE_MAX_BYTES), SeedBundle.class);
				String bundleId = bundle.bundleId() == null || bundle.bundleId().isBlank()
					? name.substring(CourseSeedSource.BUNDLE_PREFIX.length(), name.length() - CourseSeedSource.BUNDLE_SUFFIX.length())
					: bundle.bundleId();
				for (SeedBundle.Course course : bundle.courses()) {
					if (course.contentId() == null || course.contentId().isBlank()) {
						log.warn("contentId 없는 코스를 건너뜁니다. file={}", name);
						continue;
					}
					courses.add(new Loaded(bundleId, course));
				}
			} catch (RuntimeException e) {
				log.warn("묶음 파일을 읽지 못해 건너뜁니다. file={}, reason={}", name, e.getClass().getSimpleName());
			}
		}
		return courses;
	}
}
