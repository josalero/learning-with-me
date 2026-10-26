package dev.mytechprofile.statefulagent.approval;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolCallDecisionRepository extends JpaRepository<ToolCallDecisionEntity, UUID> {

    List<ToolCallDecisionEntity> findByApprovalId(UUID approvalId);
}
