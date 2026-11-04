package com.ssafy.yeogirok.domain.footprint.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.footprint.FootprintProperties;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateCommand;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateResponse;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.footprint.entity.FootprintBody;
import com.ssafy.yeogirok.domain.footprint.entity.FootprintCategories;
import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.GeoDistance;
import com.ssafy.yeogirok.global.geo.ReverseGeocodeResult;
import com.ssafy.yeogirok.global.geo.ReverseGeocoder;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.lock.UserLock;

/**
 * API-041 발자취·방명록 저장. 멱등키 처리({@code IdempotencyService})는 컨트롤러가 이 서비스 밖에서 한다.
 *
 * <ol>
 *   <li>값 검사(좌표 범위 C011, 카테고리·본문 C003, 위치 증거 유효 시각 C009). 외부 호출 전에 끝낸다.</li>
 *   <li>역지오코딩(주소·위치명·법정동 코드). 외부 호출이라 DB 트랜잭션과 사용자 잠금 밖에서 한다. 실패해도 저장은 계속한다.</li>
 *   <li>트랜잭션 안에서 사용자 잠금 → 작성 제한 검사(F003) → 저장.</li>
 * </ol>
 */
@Service
public class FootprintCreateService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	/** 작성 제한 구간의 하루 시작 시각(노션: Asia/Seoul 04:00 이상 다음 날 04:00 미만). */
	private static final LocalTime DAY_START = LocalTime.of(4, 0);

	private final FootprintRepository footprintRepository;
	private final FootprintFinder footprintFinder;
	private final UserLock userLock;
	private final LocationEvidencePolicy locationEvidencePolicy;
	private final ReverseGeocoder reverseGeocoder;
	private final FootprintProperties properties;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public FootprintCreateService(FootprintRepository footprintRepository, FootprintFinder footprintFinder,
		UserLock userLock, LocationEvidencePolicy locationEvidencePolicy, ReverseGeocoder reverseGeocoder,
		FootprintProperties properties, PlatformTransactionManager transactionManager, Clock clock) {
		this.footprintRepository = footprintRepository;
		this.footprintFinder = footprintFinder;
		this.userLock = userLock;
		this.locationEvidencePolicy = locationEvidencePolicy;
		this.reverseGeocoder = reverseGeocoder;
		this.properties = properties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public FootprintCreateResponse create(Long userId, FootprintCreateCommand command) {
		locationEvidencePolicy.checkCoordinates(command.latitude(), command.longitude());
		List<String> categoryCodes = FootprintCategories.validate(command.categoryCodes());
		String body = FootprintBody.normalize(command.body());
		locationEvidencePolicy.checkFresh(command.capturedAt(), clock.instant());

		ReverseGeocodeResult place = reverseGeocoder.reverseGeocode(command.latitude(), command.longitude());

		return transactionTemplate.execute(status -> save(userId, command, categoryCodes, body, place));
	}

	/** 같은 멱등키로 다시 보낸 요청에 저장된 발자취를 돌려준다. 그사이 삭제했으면 F001. */
	@Transactional(readOnly = true)
	public FootprintCreateResponse getCreated(Long userId, Long footprintId) {
		return FootprintCreateResponse.from(footprintFinder.getOwnFootprint(footprintId, userId));
	}

	private FootprintCreateResponse save(Long userId, FootprintCreateCommand command, List<String> categoryCodes,
		String body, ReverseGeocodeResult place) {
		// 같은 사용자의 생성·삭제를 한 줄로 세운다. 잠금을 잡은 뒤에 읽어야 먼저 커밋된 발자취가 보인다.
		userLock.lock(userId);
		Instant dayStart = dayStartOf(clock.instant());
		rejectIfTooCloseToday(userId, command, dayStart);

		Footprint footprint = footprintRepository.saveAndFlush(Footprint.builder()
			.userId(userId)
			.latitude(command.latitude())
			.longitude(command.longitude())
			.locationName(place.locationName())
			.address(place.address())
			.legalAreaCode(place.legalAreaCode())
			.body(body)
			.categoryCodes(categoryCodes)
			.visitedAt(command.capturedAt())
			.locationCapturedAt(command.capturedAt())
			.locationPolicyVersion(locationEvidencePolicy.policyVersion())
			.build());
		return FootprintCreateResponse.from(footprint);
	}

	private void rejectIfTooCloseToday(Long userId, FootprintCreateCommand command, Instant dayStart) {
		int radius = properties.sameDayRadiusMeters();
		boolean tooClose = footprintRepository
			.findActiveCreatedBetween(userId, dayStart, dayStart.plus(Duration.ofDays(1)))
			.stream()
			.anyMatch(existing -> GeoDistance.meters(existing.getLatitude(), existing.getLongitude(),
				command.latitude(), command.longitude()) <= radius);
		if (tooClose) {
			throw new BusinessException(ExceptionType.FOOTPRINT_TOO_CLOSE_TODAY);
		}
	}

	/** {@code now} 가 속한 작성 제한 구간의 시작(Asia/Seoul 04:00). 서버 시각 기준이며 기기 시각·capturedAt 은 쓰지 않는다. */
	static Instant dayStartOf(Instant now) {
		ZonedDateTime seoul = now.atZone(SEOUL);
		LocalDate day = seoul.toLocalTime().isBefore(DAY_START) ? seoul.toLocalDate().minusDays(1) : seoul.toLocalDate();
		return day.atTime(DAY_START).atZone(SEOUL).toInstant();
	}
}
