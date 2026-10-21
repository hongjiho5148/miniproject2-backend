package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 로그인 실패 횟수 기록. 로그인 트랜잭션은 실패 시 예외로 롤백되므로,
 * 실패 기록이 함께 사라지지 않도록 별도 트랜잭션(REQUIRES_NEW)으로 커밋한다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        // 존재하지 않는 이메일은 기록하지 않는다 (계정 존재 여부를 응답으로 구분할 수 없게 하기 위함)
        userRepository.findByEmail(email)
                .ifPresent(user -> user.recordLoginFailure(LocalDateTime.now(), MAX_ATTEMPTS, LOCK_DURATION));
    }
}
