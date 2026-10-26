package dev.mytechprofile.statefulagent.business;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.persistence.EntityManager;

@Service
public class HiringService {

    private static final Logger log = LoggerFactory.getLogger(HiringService.class);

    static final String ADVANCE = "ADVANCE";
    static final String SCORECARD = "SCORECARD";
    static final String OFFER_EXTENDED = "OFFER_EXTENDED";
    static final String OFFER_RESPONSE = "OFFER_RESPONSE";
    static final String REJECT = "REJECT";
    static final String EXTENDED = "EXTENDED";

    private final JobApplicationRepository applications;
    private final HiringActionRepository actions;
    private final InterviewScorecardRepository scorecards;
    private final JobOfferRepository offers;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ObservationRegistry observations;

    public HiringService(JobApplicationRepository applications, HiringActionRepository actions,
            InterviewScorecardRepository scorecards, JobOfferRepository offers, EntityManager entityManager, Clock clock,
            ObservationRegistry observations) {
        this.applications = applications;
        this.actions = actions;
        this.scorecards = scorecards;
        this.offers = offers;
        this.entityManager = entityManager;
        this.clock = clock;
        this.observations = observations;
    }

    @Transactional(readOnly = true)
    public JobApplicationEntity requireApplication(String tenantId, String applicationId) {
        return this.applications.findById(applicationId)
                .filter(application -> tenantId.equals(application.getTenantId()))
                .orElseThrow(() -> new HiringRuleException("APPLICATION_NOT_FOUND", "Application was not found."));
    }

    @Transactional
    public HiringResult advance(String tenantId, String applicationId, HiringStage to, String idempotencyKey) {
        return observe("business.application.advance", ADVANCE, tenantId, applicationId,
                () -> applyAdvance(tenantId, applicationId, to, idempotencyKey));
    }

    @Transactional
    public HiringResult recordScorecard(String tenantId, String applicationId, InterviewRound round,
            Recommendation recommendation, String idempotencyKey) {
        return observe("business.application.scorecard", SCORECARD, tenantId, applicationId,
                () -> applyScorecard(tenantId, applicationId, round, recommendation, idempotencyKey));
    }

    @Transactional
    public HiringResult extendOffer(String tenantId, String applicationId, String level, BigDecimal baseSalary,
            String idempotencyKey) {
        return observe("business.offer.extend", OFFER_EXTENDED, tenantId, applicationId,
                () -> applyOffer(tenantId, applicationId, level, baseSalary, idempotencyKey));
    }

    @Transactional
    public HiringResult recordOfferResponse(String tenantId, String applicationId, OfferResponse response,
            String idempotencyKey) {
        return observe("business.offer.respond", OFFER_RESPONSE, tenantId, applicationId,
                () -> applyResponse(tenantId, applicationId, response, idempotencyKey));
    }

    @Transactional
    public HiringResult reject(String tenantId, String applicationId, RejectReason reason, String idempotencyKey) {
        return observe("business.application.reject", REJECT, tenantId, applicationId,
                () -> applyReject(tenantId, applicationId, reason, idempotencyKey));
    }

    private HiringResult applyAdvance(String tenantId, String applicationId, HiringStage to, String idempotencyKey) {
        Optional<HiringActionEntity> existing = this.actions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return alreadyRecorded(applicationId, existing.get());
        }
        JobApplicationEntity application = requireApplication(tenantId, applicationId);
        StageTransitions.requireAdvance(application.getStage(), to);
        InterviewRound gate = StageTransitions.gatingRound(to);
        if (gate != null) {
            Recommendation recommendation = this.scorecards
                    .findFirstByTenantIdAndApplicationIdAndRoundOrderByCreatedAtDesc(tenantId, applicationId, gate)
                    .map(InterviewScorecardEntity::getRecommendation)
                    .orElse(null);
            if (recommendation == null || !recommendation.passing()) {
                throw new HiringRuleException("SCORECARD_NOT_PASSING", "A passing scorecard is required before this stage.");
            }
        }
        HiringStage from = application.getStage();
        if (!insertAction(tenantId, applicationId, ADVANCE, from, to, null, idempotencyKey)) {
            return alreadyRecorded(applicationId, this.actions.findByIdempotencyKey(idempotencyKey).orElseThrow());
        }
        application.setStage(to);
        log.info("{} moved from {} to {}.", applicationId, from, to);
        return HiringResult.created(ADVANCE, to, idempotencyKey);
    }

    private HiringResult applyScorecard(String tenantId, String applicationId, InterviewRound round,
            Recommendation recommendation, String idempotencyKey) {
        Optional<HiringActionEntity> existing = this.actions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return alreadyRecorded(applicationId, existing.get());
        }
        JobApplicationEntity application = requireApplication(tenantId, applicationId);
        if (application.getStage().terminal()) {
            throw new HiringRuleException("TERMINAL_STAGE", "The application is already closed.");
        }
        if (!application.getStage().name().equals(round.name())) {
            throw new HiringRuleException("ROUND_MISMATCH", "The scorecard round does not match the current stage.");
        }
        if (!insertAction(tenantId, applicationId, SCORECARD, application.getStage(), application.getStage(),
                recommendation.name(), idempotencyKey)) {
            return alreadyRecorded(applicationId, this.actions.findByIdempotencyKey(idempotencyKey).orElseThrow());
        }
        insertScorecard(tenantId, applicationId, round, recommendation, idempotencyKey);
        log.info("{} now has a {} scorecard for {}. The stage stays {}.", applicationId, recommendation, round, application.getStage());
        return HiringResult.created(SCORECARD, application.getStage(), idempotencyKey);
    }

    private HiringResult applyOffer(String tenantId, String applicationId, String level, BigDecimal baseSalary,
            String idempotencyKey) {
        Optional<HiringActionEntity> existing = this.actions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return alreadyRecorded(applicationId, existing.get());
        }
        JobApplicationEntity application = requireApplication(tenantId, applicationId);
        if (application.getStage() != HiringStage.OFFER) {
            throw new HiringRuleException("ILLEGAL_TRANSITION", "An offer can be extended only from the offer stage.");
        }
        if (baseSalary == null || baseSalary.compareTo(application.getBandMin()) < 0) {
            throw new HiringRuleException("OFFER_OUT_OF_BAND", "The salary is below the role band.");
        }
        if (this.offers.findByTenantIdAndApplicationId(tenantId, applicationId).isPresent()) {
            throw new HiringRuleException("OFFER_ALREADY_EXTENDED", "An offer already exists for this application.");
        }
        if (!insertAction(tenantId, applicationId, OFFER_EXTENDED, HiringStage.OFFER, HiringStage.OFFER, level, idempotencyKey)) {
            return alreadyRecorded(applicationId, this.actions.findByIdempotencyKey(idempotencyKey).orElseThrow());
        }
        insertOffer(tenantId, applicationId, level, baseSalary, idempotencyKey);
        log.info("Offer extended for {}: level {}, base salary {}. The stage stays OFFER.", applicationId, level, baseSalary.toPlainString());
        return HiringResult.created(OFFER_EXTENDED, HiringStage.OFFER, idempotencyKey);
    }

    private HiringResult applyResponse(String tenantId, String applicationId, OfferResponse response, String idempotencyKey) {
        Optional<HiringActionEntity> existing = this.actions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return alreadyRecorded(applicationId, existing.get());
        }
        JobApplicationEntity application = requireApplication(tenantId, applicationId);
        JobOfferEntity offer = this.offers.findByTenantIdAndApplicationId(tenantId, applicationId)
                .filter(row -> EXTENDED.equals(row.getStatus()))
                .orElseThrow(() -> new HiringRuleException("NO_OPEN_OFFER", "There is no extended offer to answer."));
        HiringStage to = response == OfferResponse.ACCEPTED ? HiringStage.HIRED : HiringStage.OFFER_DECLINED;
        if (!insertAction(tenantId, applicationId, OFFER_RESPONSE, application.getStage(), to, response.name(), idempotencyKey)) {
            return alreadyRecorded(applicationId, this.actions.findByIdempotencyKey(idempotencyKey).orElseThrow());
        }
        offer.setStatus(response.name());
        offer.setRespondedAt(this.clock.instant());
        application.setStage(to);
        log.info("{} {}. The stage is now {}.", applicationId,
                response == OfferResponse.ACCEPTED ? "accepted the offer" : "declined the offer", to);
        return HiringResult.created(OFFER_RESPONSE, to, idempotencyKey);
    }

    private HiringResult applyReject(String tenantId, String applicationId, RejectReason reason, String idempotencyKey) {
        Optional<HiringActionEntity> existing = this.actions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return alreadyRecorded(applicationId, existing.get());
        }
        JobApplicationEntity application = requireApplication(tenantId, applicationId);
        if (application.getStage().terminal()) {
            throw new HiringRuleException("TERMINAL_STAGE", "The application is already closed.");
        }
        if (this.offers.findByTenantIdAndApplicationId(tenantId, applicationId).isPresent()) {
            throw new HiringRuleException("ILLEGAL_TRANSITION", "Record the offer response instead of rejecting.");
        }
        HiringStage from = application.getStage();
        if (!insertAction(tenantId, applicationId, REJECT, from, HiringStage.REJECTED, reason.name(), idempotencyKey)) {
            return alreadyRecorded(applicationId, this.actions.findByIdempotencyKey(idempotencyKey).orElseThrow());
        }
        application.setStage(HiringStage.REJECTED);
        log.info("{} rejected ({}). The stage moved from {} to REJECTED.", applicationId, reason, from);
        return HiringResult.created(REJECT, HiringStage.REJECTED, idempotencyKey);
    }

    private boolean insertAction(String tenantId, String applicationId, String action, HiringStage from, HiringStage to,
            String reason, String idempotencyKey) {
        int inserted = this.entityManager.createNativeQuery("""
                INSERT INTO hiring_action
                    (id, tenant_id, application_id, action, from_stage, to_stage, reason, idempotency_key, created_at)
                VALUES
                    (:id, :tenantId, :applicationId, :action, :fromStage, :toStage, :reason, :idempotencyKey, :createdAt)
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenantId", tenantId)
                .setParameter("applicationId", applicationId)
                .setParameter("action", action)
                .setParameter("fromStage", from.name())
                .setParameter("toStage", to.name())
                .setParameter("reason", reason)
                .setParameter("idempotencyKey", idempotencyKey)
                .setParameter("createdAt", this.clock.instant())
                .executeUpdate();
        return inserted == 1;
    }

    private void insertScorecard(String tenantId, String applicationId, InterviewRound round, Recommendation recommendation,
            String idempotencyKey) {
        int score = recommendation.score();
        this.entityManager.createNativeQuery("""
                INSERT INTO interview_scorecard
                    (id, tenant_id, application_id, round, recommendation, problem_solving, code_quality,
                     communication, role_depth, idempotency_key, created_at)
                VALUES
                    (:id, :tenantId, :applicationId, :round, :recommendation, :score, :score, :score, :score,
                     :idempotencyKey, :createdAt)
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenantId", tenantId)
                .setParameter("applicationId", applicationId)
                .setParameter("round", round.name())
                .setParameter("recommendation", recommendation.name())
                .setParameter("score", score)
                .setParameter("idempotencyKey", idempotencyKey)
                .setParameter("createdAt", this.clock.instant())
                .executeUpdate();
    }

    private void insertOffer(String tenantId, String applicationId, String level, BigDecimal baseSalary, String idempotencyKey) {
        int inserted = this.entityManager.createNativeQuery("""
                INSERT INTO job_offer
                    (id, tenant_id, application_id, level, base_salary, status, idempotency_key, created_at)
                VALUES
                    (:id, :tenantId, :applicationId, :level, :baseSalary, :status, :idempotencyKey, :createdAt)
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenantId", tenantId)
                .setParameter("applicationId", applicationId)
                .setParameter("level", level)
                .setParameter("baseSalary", baseSalary)
                .setParameter("status", EXTENDED)
                .setParameter("idempotencyKey", idempotencyKey)
                .setParameter("createdAt", this.clock.instant())
                .executeUpdate();
        if (inserted == 0) {
            throw new HiringRuleException("OFFER_ALREADY_EXTENDED", "An offer already exists for this application.");
        }
    }

    private HiringResult observe(String name, String action, String tenantId, String applicationId,
            java.util.function.Supplier<HiringResult> work) {
        try {
            return Observation.createNotStarted(name, this.observations)
                    .lowCardinalityKeyValue("hiring.action", action)
                    .highCardinalityKeyValue("tenant.id", tenantId)
                    .highCardinalityKeyValue("application.id", applicationId)
                    .observe(work);
        }
        catch (HiringRuleException exception) {
            log.warn("{}: {}", applicationId, exception.getMessage());
            throw exception;
        }
    }

    private HiringResult alreadyRecorded(String applicationId, HiringActionEntity prior) {
        log.info("{} already has this {} ({} to {}). Returning the stored result.", applicationId, prior.getAction(),
                prior.getFromStage(), prior.getToStage());
        return HiringResult.replayed(prior);
    }

    public record HiringResult(boolean created, String action, HiringStage stage, String idempotencyKey) {

        static HiringResult created(String action, HiringStage stage, String idempotencyKey) {
            return new HiringResult(true, action, stage, idempotencyKey);
        }

        static HiringResult replayed(HiringActionEntity entry) {
            return new HiringResult(false, entry.getAction(), entry.getToStage(), entry.getIdempotencyKey());
        }
    }
}
