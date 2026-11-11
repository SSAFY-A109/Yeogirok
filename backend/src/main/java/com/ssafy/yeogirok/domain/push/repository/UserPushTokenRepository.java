package com.ssafy.yeogirok.domain.push.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.push.entity.UserPushToken;

/** 토큰이 기본키다(노션 ERD 20번). 토큰 하나로 찾을 때는 {@code findById(token)}. */
public interface UserPushTokenRepository extends JpaRepository<UserPushToken, String> {

	/**
	 * 그 회원의 그 토큰만 지운다. API-096(본인 토큰만), FCM UNREGISTERED(보낼 당시 주인과 같을 때만)에서 쓴다.
	 * 그사이 다른 계정으로 옮겨 간 토큰은 지우지 않는다.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from UserPushToken t where t.userId = :userId and t.token = :token")
	int deleteByUserIdAndToken(@Param("userId") Long userId, @Param("token") String token);

	/**
	 * 탈퇴. 그 회원의 토큰을 모두 지운다. 탈퇴 트랜잭션 안에서 부른다. 영속성 컨텍스트를 비우지 않는다(같은 트랜잭션에서 잠근
	 * 회원 엔티티가 분리되면 뒤이은 변경이 저장되지 않는다). 그 트랜잭션은 토큰 엔티티를 읽지 않으므로 비울 것이 없다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("delete from UserPushToken t where t.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);

	/** 그 회원의 등록 기기 토큰. 먼저 등록한 순서. */
	@Query("select t.token from UserPushToken t where t.userId = :userId order by t.createdAt, t.token")
	List<String> findTokensByUserId(@Param("userId") Long userId);
}
