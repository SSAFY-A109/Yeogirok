package com.ssafy.yeogirok.domain.room.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomDetailResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomChange;
import com.ssafy.yeogirok.domain.room.event.RoomChangedEvent;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

@DataJpaTest
@Import(RoomTestConfig.class)
@RecordApplicationEvents
class RoomServiceTest {

	static final Long OWNER = 2001L;
	static final Long FRIEND = 2002L;
	static final Long STRANGER = 2003L;
	/** NOW(2026-11-05 Asia/Seoul) 기준 */
	static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;
	@Autowired
	ApplicationEvents events;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, STRANGER);
	}

	private Long createRoom(Long userId) {
		return roomService.create(userId, new RoomCreateCommand("가을 여행", TODAY, TODAY.plusDays(1),
			TransportMode.CAR)).id();
	}

	private void join(Long userId, Long roomId) {
		String token = roomInvitationService.getOrCreateInvitation(ownerOf(roomId), roomId).inviteToken();
		roomInvitationService.accept(userId, token);
	}

	private Long ownerOf(Long roomId) {
		return em.getEntityManager()
			.createQuery("select m.userId from RoomMember m where m.roomId = :roomId and m.creator = true", Long.class)
			.setParameter("roomId", roomId)
			.getSingleResult();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	/* ── API-021 생성 ───────────────────────────────────────── */

	@Test
	void 방을_만들면_생성자가_첫_참여자다() {
		RoomCreateResponse response = roomService.create(OWNER,
			new RoomCreateCommand("  가을 여행  ", TODAY, TODAY.plusDays(1), TransportMode.TRANSIT));
		em.clear();

		assertThat(response.version()).isEqualTo(1);
		assertThat(response.isCreator()).isTrue();
		TravelRoom room = em.find(TravelRoom.class, response.id());
		assertThat(room.getTitle()).isEqualTo("가을 여행");
		assertThat(room.getTransportMode()).isEqualTo(TransportMode.TRANSIT);
		assertThat(room.getCreatedAt()).isEqualTo(NOW);
		RoomMember member = em.find(RoomMember.class, new RoomMemberId(response.id(), OWNER));
		assertThat(member.isCreator()).isTrue();
		assertThat(member.isCurrent()).isTrue();
		assertThat(member.getLastReadMessageId()).isNull();
	}

	@Test
	void 날짜는_둘_다_미정이어도_된다() {
		Long id = roomService.create(OWNER, new RoomCreateCommand("미정", null, null, TransportMode.CAR)).id();

		assertThat(em.find(TravelRoom.class, id).getStartDate()).isNull();
	}

	@Test
	void 날짜가_한쪽만_있거나_순서가_틀리거나_오늘_이전이면_C012() {
		assertError(() -> roomService.create(OWNER, new RoomCreateCommand("t", TODAY, null, TransportMode.CAR)),
			ExceptionType.INVALID_DATE_RANGE);
		assertError(() -> roomService.create(OWNER,
			new RoomCreateCommand("t", TODAY.plusDays(2), TODAY.plusDays(1), TransportMode.CAR)),
			ExceptionType.INVALID_DATE_RANGE);
		assertError(() -> roomService.create(OWNER,
			new RoomCreateCommand("t", TODAY.minusDays(1), TODAY, TransportMode.CAR)),
			ExceptionType.INVALID_DATE_RANGE);
	}

	@Test
	void 제목이_비었거나_200자를_넘으면_C003() {
		assertError(() -> roomService.create(OWNER, new RoomCreateCommand("   ", null, null, TransportMode.CAR)),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> roomService.create(OWNER,
			new RoomCreateCommand("가".repeat(201), null, null, TransportMode.CAR)), ExceptionType.NOT_ALLOWED_VALUE);
	}

	/**
	 * PostgreSQL VARCHAR(200) 은 문자(코드 포인트) 단위라 이모지 200개가 들어간다(로컬 PostgreSQL 16 에서 확인).
	 * H2 는 UTF-16 단위로 세어 저장까지 가면 거부되므로 검사만 본다.
	 */
	@Test
	void 제목_길이는_이모지도_1자로_센다() {
		String emoji200 = "😀".repeat(200);

		assertThat(RoomService.normalizeTitle(" " + emoji200 + " ")).isEqualTo(emoji200);
		assertError(() -> RoomService.normalizeTitle(emoji200 + "😀"), ExceptionType.NOT_ALLOWED_VALUE);
	}

	/* ── API-020 목록 ───────────────────────────────────────── */

	@Test
	void 목록은_현재_참여_중인_방만_최근_순으로_나눠_준다() {
		Long first = createRoom(OWNER);
		Long second = createRoom(OWNER);
		Long third = createRoom(OWNER);
		Long others = createRoom(FRIEND);
		Long left = createRoom(OWNER);
		join(FRIEND, left);
		roomService.leave(OWNER, left);

		RoomListResponse page1 = roomService.getMyRooms(OWNER, null, 2);
		assertThat(page1.items()).extracting(RoomListResponse.Item::id).containsExactly(third, second);
		assertThat(page1.nextCursor()).isNotNull();

		RoomListResponse page2 = roomService.getMyRooms(OWNER, page1.nextCursor(), 2);
		assertThat(page2.items()).extracting(RoomListResponse.Item::id).containsExactly(first);
		assertThat(page2.nextCursor()).isNull();
		assertThat(others).isNotIn(page1.items().stream().map(RoomListResponse.Item::id).toList());
	}

	@Test
	void 목록_항목에는_참여자와_내_읽음_상태가_있다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);

		RoomListResponse.Item item = roomService.getMyRooms(FRIEND, null, null).items().getFirst();

		assertThat(item.memberCount()).isEqualTo(2);
		assertThat(item.members()).extracting(RoomListResponse.Member::userId).containsExactly(OWNER, FRIEND);
		assertThat(item.placeCount()).isZero();
		assertThat(item.recordIds()).isEmpty();
		assertThat(item.chatReadState().lastReadMessageId()).isNull();
		assertThat(item.chatReadState().hasUnreadMessages()).isFalse();
	}

	@Test
	void 탈퇴한_회원은_참여자_목록과_인원에서_빠진다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);
		em.getEntityManager().createNativeQuery("UPDATE users SET status = 'WITHDRAWN' WHERE id = :id")
			.setParameter("id", FRIEND).executeUpdate();

		RoomListResponse.Item item = roomService.getMyRooms(OWNER, null, null).items().getFirst();

		assertThat(item.memberCount()).isEqualTo(1);
	}

	@Test
	void 목록_limit은_1에서_50이고_커서가_잘못되면_C006() {
		assertError(() -> roomService.getMyRooms(OWNER, null, 0), ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
		assertError(() -> roomService.getMyRooms(OWNER, null, 51), ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
		assertError(() -> roomService.getMyRooms(OWNER, "!!!", null), ExceptionType.INVALID_CURSOR);
		assertError(() -> roomService.getMyRooms(OWNER, "YWJj", null), ExceptionType.INVALID_CURSOR);
	}

	/* ── API-022 상세 ───────────────────────────────────────── */

	@Test
	void 상세에는_참여자와_생성자_표시가_있다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);

		RoomDetailResponse detail = roomService.getRoom(FRIEND, roomId);

		assertThat(detail.members()).extracting(RoomDetailResponse.Member::userId, RoomDetailResponse.Member::isCreator)
			.containsExactly(tuple(OWNER, true), tuple(FRIEND, false));
		assertThat(detail.version()).isEqualTo(1);
		assertThat(detail.places()).isEmpty();
		assertThat(detail.schedule()).isNull();
	}

	@Test
	void 없는_방은_RO001_참여자가_아니면_RO002() {
		Long roomId = createRoom(OWNER);

		assertError(() -> roomService.getRoom(OWNER, 999_999L), ExceptionType.ROOM_NOT_FOUND);
		assertError(() -> roomService.getRoom(STRANGER, roomId), ExceptionType.ROOM_NOT_MEMBER);
	}

	@Test
	void 나간_사람은_상세를_볼_수_없다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);
		roomService.leave(FRIEND, roomId);

		assertError(() -> roomService.getRoom(FRIEND, roomId), ExceptionType.ROOM_NOT_MEMBER);
	}

	/* ── API-023 수정 ───────────────────────────────────────── */

	@Test
	void 수정하면_보낸_값만_바뀌고_version이_오른다() {
		Long roomId = createRoom(OWNER);

		RoomUpdateResponse response = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(1, null, false, null, null, TransportMode.TRANSIT, false));
		em.clear();

		assertThat(response.applied()).isTrue();
		assertThat(response.version()).isEqualTo(2);
		assertThat(response.affectedItems()).isEmpty();
		TravelRoom room = em.find(TravelRoom.class, roomId);
		assertThat(room.getTransportMode()).isEqualTo(TransportMode.TRANSIT);
		assertThat(room.getTitle()).isEqualTo("가을 여행");
		assertThat(room.getStartDate()).isEqualTo(TODAY);
	}

	@Test
	void 참여자는_누구나_수정할_수_있고_날짜를_미정으로_바꿀_수_있다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);

		roomService.update(FRIEND, roomId, new RoomUpdateCommand(1, "새 제목", true, null, null, null, false));
		em.clear();

		TravelRoom room = em.find(TravelRoom.class, roomId);
		assertThat(room.getTitle()).isEqualTo("새 제목");
		assertThat(room.getStartDate()).isNull();
		assertThat(room.getEndDate()).isNull();
	}

	@Test
	void 미리보기는_아무것도_바꾸지_않는다() {
		Long roomId = createRoom(OWNER);

		RoomUpdateResponse response = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(1, "바뀌면 안 됨", false, null, null, null, true));
		em.clear();

		assertThat(response.applied()).isFalse();
		assertThat(response.version()).isEqualTo(1);
		assertThat(em.find(TravelRoom.class, roomId).getTitle()).isEqualTo("가을 여행");
	}

	@Test
	void version이_다르면_C004_참여자가_아니면_RO002() {
		Long roomId = createRoom(OWNER);

		assertError(() -> roomService.update(OWNER, roomId,
			new RoomUpdateCommand(2, "t", false, null, null, null, false)), ExceptionType.VERSION_MISMATCH);
		assertError(() -> roomService.update(STRANGER, roomId,
			new RoomUpdateCommand(1, "t", false, null, null, null, false)), ExceptionType.ROOM_NOT_MEMBER);
	}

	@Test
	void 출발일을_오늘_이전으로_바꾸면_C012_그대로_두면_지나도_된다() {
		Long roomId = createRoom(OWNER);
		clock.advance(Duration.ofDays(3));

		assertError(() -> roomService.update(OWNER, roomId,
				new RoomUpdateCommand(1, null, true, TODAY.plusDays(1), TODAY.plusDays(4), null, false)),
			ExceptionType.INVALID_DATE_RANGE);
		// 출발일은 그대로 두고 종료일만 늘리는 것은 된다.
		RoomUpdateResponse response = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(1, null, true, TODAY, TODAY.plusDays(5), null, false));
		assertThat(response.applied()).isTrue();
	}

	@Test
	void 초대_링크를_만들어도_방_version은_그대로다() {
		Long roomId = createRoom(OWNER);
		// 시각이 같으면 updatedAt 이 그대로라 변경으로 잡히지 않는다. 시계를 돌려야 version 증가 여부가 드러난다.
		clock.advance(Duration.ofMinutes(1));
		roomInvitationService.getOrCreateInvitation(OWNER, roomId);
		em.flush();
		em.clear();

		assertThat(em.find(TravelRoom.class, roomId).getVersion()).isEqualTo(1);
	}

	@Test
	void 다른_참여자가_초대_링크를_만들어도_보고_있던_version으로_방을_수정할_수_있다() {
		Long roomId = createRoom(OWNER);
		int seen = roomService.getRoom(OWNER, roomId).version();
		clock.advance(Duration.ofMinutes(1));
		roomInvitationService.getOrCreateInvitation(OWNER, roomId);
		em.flush();
		em.clear();

		assertThat(roomService.update(OWNER, roomId,
			new RoomUpdateCommand(seen, "새 제목", false, null, null, null, false)).version()).isEqualTo(seen + 1);
	}

	/* ── API-087 나가기 ─────────────────────────────────────── */

	@Test
	void 나가면_현재_참여자에서_빠지고_방은_남는다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);

		roomService.leave(OWNER, roomId);
		em.clear();

		assertThat(em.find(RoomMember.class, new RoomMemberId(roomId, OWNER)).isCurrent()).isFalse();
		assertThat(em.find(TravelRoom.class, roomId).isDeleted()).isFalse();
		assertThat(roomService.getRoom(FRIEND, roomId).members()).hasSize(1);
	}

	@Test
	void 마지막_참여자가_나가면_방이_소프트_삭제된다() {
		Long roomId = createRoom(OWNER);

		roomService.leave(OWNER, roomId);
		em.flush();
		em.clear();

		assertThat(em.find(TravelRoom.class, roomId).getDeletedAt()).isEqualTo(NOW);
		assertError(() -> roomService.getRoom(OWNER, roomId), ExceptionType.ROOM_NOT_FOUND);
	}

	@Test
	void 나가기_재요청은_그대로_성공한다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);
		roomService.leave(FRIEND, roomId);

		roomService.leave(FRIEND, roomId);

		assertThat(roomService.getRoom(OWNER, roomId).members()).hasSize(1);
		// 마지막 참여자가 나가 삭제된 방에서도 참여 이력이 있으면 재요청은 성공한다.
		roomService.leave(OWNER, roomId);
		roomService.leave(OWNER, roomId);
	}

	@Test
	void 참여_이력이_없으면_RO002_없는_방이나_삭제된_방이면_RO001() {
		Long roomId = createRoom(OWNER);
		Long deleted = createRoom(OWNER);
		roomService.leave(OWNER, deleted);

		assertError(() -> roomService.leave(STRANGER, roomId), ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> roomService.leave(OWNER, 999_999L), ExceptionType.ROOM_NOT_FOUND);
		assertError(() -> roomService.leave(STRANGER, deleted), ExceptionType.ROOM_NOT_FOUND);
	}

	@Test
	void 만든_방_재조회는_현재_참여자에게만_준다() {
		Long roomId = createRoom(OWNER);

		assertThat(roomService.getCreated(OWNER, roomId)).isEqualTo(new RoomCreateResponse(roomId, 1, true));
		assertError(() -> roomService.getCreated(STRANGER, roomId), ExceptionType.ROOM_NOT_MEMBER);
	}

	/* ── 실시간 알림(ROOM_UPDATED) 이벤트 ─────────────────────── */

	private List<RoomChangedEvent> roomEvents() {
		return events.stream(RoomChangedEvent.class).toList();
	}

	@Test
	void 방_정보를_바꾸면_ROOM_변경을_알리고_날짜나_이동_수단이_바뀌면_SCHEDULE_도_알린다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);
		events.clear();

		RoomUpdateResponse titled = roomService.update(FRIEND, roomId,
			new RoomUpdateCommand(1, "새 제목", false, null, null, null, false));
		assertThat(roomEvents()).containsExactly(RoomChangedEvent.of(roomId, FRIEND, titled.version(), RoomChange.ROOM));

		events.clear();
		RoomUpdateResponse moved = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(titled.version(), null, false, null, null, TransportMode.TRANSIT, false));
		assertThat(roomEvents()).containsExactly(
			RoomChangedEvent.of(roomId, OWNER, moved.version(), RoomChange.ROOM, RoomChange.SCHEDULE));
	}

	@Test
	void 미리보기나_같은_값으로_보낸_수정은_알리지_않는다() {
		Long roomId = createRoom(OWNER);
		events.clear();

		roomService.update(OWNER, roomId, new RoomUpdateCommand(1, "바뀌면 안 됨", false, null, null, null, true));
		RoomUpdateResponse same = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(1, "가을 여행", false, null, null, TransportMode.CAR, false));

		assertThat(same.version()).isEqualTo(1);
		assertThat(roomEvents()).isEmpty();
	}

	@Test
	void 참여하거나_나가면_MEMBERS_변경을_알리고_방이_삭제되면_알리지_않는다() {
		Long roomId = createRoom(OWNER);
		events.clear();

		join(FRIEND, roomId);
		assertThat(roomEvents()).containsExactly(RoomChangedEvent.of(roomId, FRIEND, 1, RoomChange.MEMBERS));

		events.clear();
		join(FRIEND, roomId);
		assertThat(roomEvents()).as("이미 참여 중이면 바뀐 것이 없다").isEmpty();

		roomService.leave(FRIEND, roomId);
		assertThat(roomEvents()).containsExactly(RoomChangedEvent.of(roomId, FRIEND, 1, RoomChange.MEMBERS));

		events.clear();
		roomService.leave(OWNER, roomId);
		assertThat(roomEvents()).isEmpty();
	}
}
