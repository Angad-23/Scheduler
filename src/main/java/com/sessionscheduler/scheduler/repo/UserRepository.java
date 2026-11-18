package com.sessionscheduler.scheduler.repo;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    List<AppUser> findByRoleInAndEnabledTrue(Collection<Role> roles);
    List<AppUser> findByRoleInOrderByFullNameAsc(Collection<Role> roles);
}
