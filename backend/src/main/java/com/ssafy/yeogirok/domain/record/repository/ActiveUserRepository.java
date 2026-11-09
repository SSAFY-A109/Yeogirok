package com.ssafy.yeogirok.domain.record.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.user.domain.User;

/** 기록 작성자 표시용 회원 상태 조회. 회원 코드(UserRepository)를 바꾸지 않으려고 따로 둔다. */
public interface ActiveUserRepository extends Repository<User, Long> {

	/** 주어진 회원 중 탈퇴하지 않은 회원의 ID. */
	@Query("select u.id from User u where u.id in :ids"
		+ " and u.status = com.ssafy.yeogirok.domain.user.domain.UserStatus.ACTIVE")
	List<Long> findActiveIds(@Param("ids") Collection<Long> ids);
}
