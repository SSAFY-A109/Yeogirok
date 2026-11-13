package com.ssafy.yeogirok.domain.course.seed;

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

	/** @param failed contentId → 사유 */
	record Report(int inserted, int skipped, Map<String, String> failed, int unpublished, int reseeded) {

		static final Report NOTHING = new Report(0, 0, Map.of(), 0, 0);
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
		int unpublished = properties.unpublish().isEmpty() ? 0
			: seeder.unpublish(properties.unpublish(), properties.unpublishAll());
		int inserted = 0;
		int skipped = 0;
		int reseeded = 0;
		Map<String, String> failed = new LinkedHashMap<>();
		for (String contentId : properties.reseed()) {
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
			if (properties.reseed().contains(contentId) || failed.containsKey(contentId)) {
				continue;
			}
			CourseSeeder.Outcome outcome = seeder.seed(loaded.bundleId(), loaded.course(), ownerId, source);
			switch (outcome.result()) {
				case INSERTED -> inserted++;
				case SKIPPED -> skipped++;
				case FAILED -> failed.put(contentId, outcome.reason());
			}
		}
		Report report = new Report(inserted, skipped, failed, unpublished, reseeded);
		log.info("공식 코스 적재 끝. 넣음={}, 건너뜀={}, 실패={}, 내림={}, 다시 넣음={}, 실패 사유={}", inserted, skipped,
			failed.size(), unpublished, reseeded, failed);
		return report;
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
