package com.sessionscheduler.scheduler.repo;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.VerificationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
    void deleteByUser(AppUser user);
}
