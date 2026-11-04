package com.ssafy.yeogirok.global.lock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 단위 잠금. users 행을 {@code SELECT ... FOR UPDATE} 로 잠가 같은 사용자의 발자취 생성·삭제와 탈퇴를 한 줄로 세운다
 * (탈퇴도 같은 {@link UserRepository#findByIdForUpdate} 를 쓴다). 트랜잭션이 끝나면 풀린다.
 */
@Component
@RequiredArgsConstructor
public class UserLock {

	private final UserRepository userRepository;

	/**
	 * 호출한 트랜잭션이 끝날 때까지 이 사용자를 잠근다. 트랜잭션 밖에서 부르면 예외다.
	 * 잠근 뒤 보니 없거나 탈퇴했으면 A003(토큰 검증의 탈퇴 차단을 지나친 요청도 여기서 막는다).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(Long userId) {
		userRepository.findByIdForUpdate(userId)
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ExceptionType.ACCESS_TOKEN_INVALID));
	}
}
