package com.codeflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface UserRepository extends JpaRepository<AppUser,Long>{ Optional<AppUser> findByEmail(String email); }
interface TaskRepository extends JpaRepository<Task,Long>{ List<Task> findByOwnerIdOrderByPriorityScoreDesc(Long ownerId); }
interface CategoryRepository extends JpaRepository<Category,Long>{}
interface TimeLogRepository extends JpaRepository<TimeLog,Long>{ List<TimeLog> findByTaskIdAndUserIdOrderByStartTimeDesc(Long taskId,Long userId); Optional<TimeLog> findFirstByUserIdAndEndTimeIsNull(Long userId); }
interface RuleRepository extends JpaRepository<PriorityRule,Long>{}
interface PasswordResetRepository extends JpaRepository<PasswordResetToken,Long>{
 long countByEmailAndCreatedAtAfter(String email,java.time.Instant createdAt);
 Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
 List<PasswordResetToken> findByEmailAndUsedAtIsNull(String email);
}
