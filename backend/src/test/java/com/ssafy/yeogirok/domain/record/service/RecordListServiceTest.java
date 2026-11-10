package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.RecordListResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress.Stage;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestUsers;

/** API-058. 참여 범위·정렬·묶음 크기·필터와 목록 항목 값. */
@DataJpaTest
@Import(RecordTestConfig.class)
class RecordListServiceTest {

	static final Long ME = 6001L;
	static final Long FRIEND = 6002L;
	static final Long WITHDRAWN = 6003L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	TestEntityManager em;

	@BeforeEach
	void setUp() {
		TestUsers.insertActive(em.getEntityManager(), ME, FRIEND);
		TestUsers.insert(em.getEntityManager(), WITHDRAWN, "WITHDRAWN");
	}

	/** @return 기록 ID */
	private Long record(String title, boolean meCurrent) {
		TravelRoom room = em.persist(TravelRoom.builder().title(title).startDate(DAY1).endDate(DAY1.plusDays(1))
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		RoomMember me = em.persist(RoomMember.creator(room.getId(), ME, NOW));
		if (!meCurrent) {
			me.leave(NOW);
		}
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW.plusSeconds(1)));
		em.persist(RoomMember.joined(room.getId(), WITHDRAWN, NOW.plusSeconds(2)));
		return em.persist(TravelRecord.create(room.getId(), title, NOW)).getId();
	}

	private void visit(Long recordId, LocalDate date, int position, String regionName) {
		Place place = em.persist(Place.manual("장소", new BigDecimal("37.7519000"), new BigDecimal("128.8761000"),
			null, null, regionName, NOW));
		ActualVisit visit = ActualVisit.manual(recordId, place.getId(), date, NOW, NOW);
		visit.moveTo(position, NOW);
		em.persist(visit);
	}

	private RecordListResponse list(Long roomId, RecordStatus status, String cursor, Integer limit) {
		em.flush();
		em.clear();
		return recordQueryService.getList(ME, roomId, status, cursor, limit);
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 현재_참여_중이고_방이_살아_있는_기록만_최근_생성_순으로_준다() {
		Long older = record("봄 여행", true);
		Long left = record("나간 여행", false);
		Long deleted = record("삭제된 방", true);
		em.find(TravelRoom.class, em.find(TravelRecord.class, deleted).getRoomId()).softDelete(NOW);
		Long newer = record("가을 여행", true);

		RecordListResponse response = list(null, null, null, null);

		assertThat(response.items()).extracting(RecordListResponse.Item::id).containsExactly(newer, older)
			.doesNotContain(left, deleted);
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	void 지역은_첫_방문_장소_방문_수_참여자_진행_단계와_게시_정보를_준다() {
		Long recordId = record("가을 여행", true);
		visit(recordId, DAY1.plusDays(1), 1, "주문진읍");
		visit(recordId, DAY1, 2, "교동");
		visit(recordId, DAY1, 1, "강문동");
		Long empty = record("빈 기록", true);
		em.flush();
		em.getEntityManager().createNativeQuery("update travel_records set published_at = ?1 where id = ?2")
			.setParameter(1, NOW).setParameter(2, recordId).executeUpdate();

		RecordListResponse response = list(null, null, null, null);

		RecordListResponse.Item item = response.items().get(1);
		assertThat(item.id()).isEqualTo(recordId);
		assertThat(item.regionName()).isEqualTo("강문동");
		assertThat(item.placeCount()).isEqualTo(3);
		assertThat(item.progress().stage()).isEqualTo(Stage.EDIT);
		assertThat(item.members()).extracting(RecordListResponse.Member::userId).containsExactly(ME, FRIEND);
		assertThat(item.memberCount()).isEqualTo(2);
		assertThat(item.startDate()).isEqualTo(DAY1);
		assertThat(item.publication()).isEqualTo(new RecordListResponse.Publication(recordId, NOW, 1));
		RecordListResponse.Item emptyItem = response.items().getFirst();
		assertThat(emptyItem.id()).isEqualTo(empty);
		assertThat(emptyItem.regionName()).isNull();
		assertThat(emptyItem.placeCount()).isZero();
		assertThat(emptyItem.progress().stage()).isEqualTo(Stage.UPLOAD);
		assertThat(emptyItem.publication()).isNull();
	}

	@Test
	void 장소_행이_없는_방문도_방문_수에_넣는다() {
		Long recordId = record("가을 여행", true);
		visit(recordId, DAY1, 1, "강문동");
		em.flush();
		em.getEntityManager().createNativeQuery("update actual_visits set place_id = place_id + 1000 where record_id = ?1")
			.setParameter(1, recordId).executeUpdate();

		RecordListResponse.Item item = list(null, null, null, null).items().getFirst();

		assertThat(item.placeCount()).isEqualTo(1);
		assertThat(item.regionName()).isNull();
	}

	@Test
	void limit_만큼씩_주고_나머지는_nextCursor_로_이어_받는다() {
		Long first = record("1", true);
		Long second = record("2", true);
		Long third = record("3", true);

		RecordListResponse page1 = list(null, null, null, 2);
		assertThat(page1.items()).extracting(RecordListResponse.Item::id).containsExactly(third, second);
		assertThat(page1.nextCursor()).isNotNull();

		RecordListResponse page2 = list(null, null, page1.nextCursor(), 2);
		assertThat(page2.items()).extracting(RecordListResponse.Item::id).containsExactly(first);
		assertThat(page2.nextCursor()).isNull();
	}

	@Test
	void 방과_상태로_거른다() {
		Long target = record("강릉", true);
		Long other = record("부산", true);
		em.find(TravelRecord.class, other).complete(NOW);
		Long roomId = em.find(TravelRecord.class, target).getRoomId();

		assertThat(list(roomId, null, null, null).items()).extracting(RecordListResponse.Item::id)
			.containsExactly(target);
		assertThat(list(null, RecordStatus.COMPLETED, null, null).items()).extracting(RecordListResponse.Item::id)
			.containsExactly(other);
		assertThat(list(roomId, RecordStatus.COMPLETED, null, null).items()).isEmpty();
	}

	@Test
	void 참여하지_않은_방을_주면_RO002() {
		Long left = record("나간 여행", false);
		Long roomId = em.find(TravelRecord.class, left).getRoomId();

		assertError(() -> list(roomId, null, null, null), ExceptionType.ROOM_NOT_MEMBER);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 51})
	void limit_범위_밖은_C007(int limit) {
		assertError(() -> list(null, null, null, limit), ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
	}

	@Test
	void 해석할_수_없는_커서는_C006() {
		assertError(() -> list(null, null, "not-a-cursor!", null), ExceptionType.INVALID_CURSOR);
	}
}
