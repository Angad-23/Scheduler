package com.sessionscheduler.scheduler.repo;

import com.sessionscheduler.scheduler.model.Proctor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProctorRepository extends JpaRepository<Proctor, Long> {
    List<Proctor> findByActiveTrueOrderByFullNameAsc();
    List<Proctor> findAllByOrderByFullNameAsc();
}
