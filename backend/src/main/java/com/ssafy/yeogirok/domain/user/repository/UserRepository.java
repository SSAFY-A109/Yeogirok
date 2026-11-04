package com.ssafy.yeogirok.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);

	/** 같은 회원의 변경(탈퇴 등)을 트랜잭션 끝까지 한 줄로 세운다. SELECT … FOR UPDATE. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);
}
