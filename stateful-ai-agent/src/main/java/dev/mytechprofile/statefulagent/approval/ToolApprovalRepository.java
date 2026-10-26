package dev.mytechprofile.statefulagent.approval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ToolApprovalRepository extends JpaRepository<ToolApprovalEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select approval from ToolApprovalEntity approval where approval.id = :id")
    Optional<ToolApprovalEntity> lockById(UUID id);

    List<ToolApprovalEntity> findBySessionIdAndStatus(String sessionId, ApprovalStatus status);

    List<ToolApprovalEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ToolApprovalEntity> findByStatusAndUpdatedAtBefore(ApprovalStatus status, Instant updatedAt);

    List<ToolApprovalEntity> findByStatus(ApprovalStatus status);
}
