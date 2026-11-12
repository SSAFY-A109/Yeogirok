package com.ssafy.yeogirok.support;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 트랜잭션 없이 커밋하는 여행방 테스트의 뒷정리. 같은 DB(PostgreSQL 등)로 다시 돌려도 users id·장소 ID 가 겹치지 않게
 * 테스트가 만든 사용자가 속한 방과 그 방의 추천 기록·후보·채팅·참여, 다른 방이 쓰지 않는 장소, 사용자를 지운다.
 */
public final class CommittedRoomData {

	private CommittedRoomData() {
	}

	public static void delete(JdbcTemplate jdbc, Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return;
		}
		String users = placeholders(userIds.size());
		Object[] userArgs = userIds.toArray();
		List<Long> roomIds = jdbc.queryForList("SELECT DISTINCT room_id FROM room_members WHERE user_id IN (" + users + ")",
			Long.class, userArgs);
		if (!roomIds.isEmpty()) {
			String rooms = placeholders(roomIds.size());
			Object[] roomArgs = roomIds.toArray();
			List<Long> placeIds = jdbc.queryForList(
				"SELECT DISTINCT place_id FROM room_places WHERE room_id IN (" + rooms + ")", Long.class, roomArgs);
			jdbc.update("DELETE FROM recommendation_logs WHERE room_id IN (" + rooms + ")", roomArgs);
			jdbc.update("DELETE FROM room_places WHERE room_id IN (" + rooms + ")", roomArgs);
			placeIds.forEach(placeId -> jdbc.update(
				"DELETE FROM places WHERE id = ? AND NOT EXISTS (SELECT 1 FROM room_places WHERE place_id = ?)",
				placeId, placeId));
			jdbc.update("DELETE FROM room_messages WHERE room_id IN (" + rooms + ")", roomArgs);
			jdbc.update("DELETE FROM room_members WHERE room_id IN (" + rooms + ")", roomArgs);
			jdbc.update("DELETE FROM travel_rooms WHERE id IN (" + rooms + ")", roomArgs);
		}
		jdbc.update("DELETE FROM users WHERE id IN (" + users + ")", userArgs);
	}

	private static String placeholders(int count) {
		return String.join(",", Collections.nCopies(count, "?"));
	}
}
