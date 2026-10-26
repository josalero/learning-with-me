package dev.mytechprofile.statefulagent.approval;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tool_call_decision")
public class ToolCallDecisionEntity {

    @Id
    private UUID id;

    @Column(name = "approval_id", nullable = false)
    private UUID approvalId;

    @Column(name = "tool_call_key", nullable = false)
    private String toolCallKey;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Column(name = "arguments_json", nullable = false)
    private String argumentsJson;

    @Column(name = "arguments_hash", nullable = false)
    private String argumentsHash;

    @Column(nullable = false)
    private String decision;

    @Column(name = "deny_message")
    private String denyMessage;

    @Column(name = "execution_status")
    private String executionStatus;

    @Column(name = "error_code")
    private String errorCode;

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getApprovalId() {
        return this.approvalId;
    }

    public void setApprovalId(UUID approvalId) {
        this.approvalId = approvalId;
    }

    public String getToolCallKey() {
        return this.toolCallKey;
    }

    public void setToolCallKey(String toolCallKey) {
        this.toolCallKey = toolCallKey;
    }

    public String getToolName() {
        return this.toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArgumentsJson() {
        return this.argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }

    public String getArgumentsHash() {
        return this.argumentsHash;
    }

    public void setArgumentsHash(String argumentsHash) {
        this.argumentsHash = argumentsHash;
    }

    public String getDecision() {
        return this.decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDenyMessage() {
        return this.denyMessage;
    }

    public void setDenyMessage(String denyMessage) {
        this.denyMessage = denyMessage;
    }

    public String getExecutionStatus() {
        return this.executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
