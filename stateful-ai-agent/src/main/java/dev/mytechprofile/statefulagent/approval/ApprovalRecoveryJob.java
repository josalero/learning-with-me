package dev.mytechprofile.statefulagent.approval;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.mytechprofile.statefulagent.platform.AgentProperties;

@Component
public class ApprovalRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(ApprovalRecoveryJob.class);

    private final ApprovalLockService locks;
    private final ApprovalWorkflow workflow;
    private final AgentProperties properties;
    private final Clock clock;

    public ApprovalRecoveryJob(ApprovalLockService locks, ApprovalWorkflow workflow, AgentProperties properties, Clock clock) {
        this.locks = locks;
        this.workflow = workflow;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${agent.approval.recovery-delay-ms:15000}")
    public void recover() {
        Instant cutoff = this.clock.instant().minus(this.properties.getApproval().getExecutionLease());
        var stale = this.locks.markStaleExecuting(cutoff);
        if (!stale.isEmpty()) {
            log.warn("{} approval(s) were still executing after the lease. They will be reconciled.", stale.size());
        }
        this.workflow.reconcile();
    }
}
