package com.ssafy.yeogirok.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
}
