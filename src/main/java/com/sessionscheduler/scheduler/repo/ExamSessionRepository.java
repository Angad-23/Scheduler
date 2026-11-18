package com.sessionscheduler.scheduler.repo;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.ExamSession;
import com.sessionscheduler.scheduler.model.Proctor;
import com.sessionscheduler.scheduler.model.SessionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {
    List<ExamSession> findByStudentOrderByStartsAtAsc(AppUser student);
    List<ExamSession> findAllByOrderByStartsAtAsc();
    List<ExamSession> findByStartsAtBetweenAndStatusNotOrderByStartsAtAsc(Instant from, Instant to, SessionStatus status);
    List<ExamSession> findByProctorAndStatus(Proctor proctor, SessionStatus status);
}
