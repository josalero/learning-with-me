package dev.mytechprofile.statefulagent.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.statefulagent.business.HiringActionEntity;
import dev.mytechprofile.statefulagent.business.HiringService;
import dev.mytechprofile.statefulagent.business.InterviewScorecardEntity;
import dev.mytechprofile.statefulagent.business.JobApplicationEntity;
import dev.mytechprofile.statefulagent.business.JobOfferEntity;
import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.AgentProperties;
import dev.mytechprofile.statefulagent.platform.SessionNotFoundException;
import dev.mytechprofile.statefulagent.tools.HiringTools;

/**
 * Reads the hiring record the tools just changed. Writes go through the agent,
 * not through this controller.
 *
 * <p><strong>When to use:</strong> after an allow, to confirm the stage, scorecard,
 * or offer. The caller must belong to the application's tenant.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * GET /api/v1/applications/APP-1
 * Authorization: Basic alice:&lt;password&gt;
 *
 * // 200, before any allow
 * { "id": "APP-1", "roleTitle": "Backend Engineer", "stage": "RECRUITER_SCREEN",
 *   "bandMin": "120000.00", "bandMax": "160000.00" }
 * }</pre>
 *
 * <p>Carol reading {@code APP-1} receives 404. {@code GET .../offer} is 404 until
 * an extend has been allowed.
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final HiringService hiring;
    private final HiringTools tools;
    private final AgentProperties properties;

    public ApplicationController(HiringService hiring, HiringTools tools, AgentProperties properties) {
        this.hiring = hiring;
        this.tools = tools;
        this.properties = properties;
    }

    /**
     * Returns the current stage and salary band.
     *
     * <p>After the collection's request 10 this is {@code TECH_SCREEN}. After
     * request 44 it is {@code HIRED}.
     *
     * @param applicationId seeded id such as {@code APP-1}
     * @return stage and band for the caller's tenant
     */
    @GetMapping("/{applicationId}")
    ApplicationView get(@PathVariable String applicationId) {
        JobApplicationEntity application = load(applicationId);
        return ApplicationView.from(application);
    }

    /**
     * Returns the hiring actions written after tools were allowed.
     *
     * <p>The list stays empty while a schedule is only {@code PENDING}. After
     * acceptance it includes the advances, the offer, and the response.
     *
     * @param applicationId application in the caller's tenant
     * @return actions in stored order
     */
    @GetMapping("/{applicationId}/actions")
    List<ActionView> actions(@PathVariable String applicationId) {
        JobApplicationEntity application = load(applicationId);
        return this.tools.actions(application.getTenantId(), application.getId()).stream().map(ActionView::from).toList();
    }

    /**
     * Returns scorecards for the application. {@code score} is the problem-solving
     * dimension, which this POC sets from the recommendation: {@code NO} is 2,
     * {@code YES} is 3, {@code STRONG_YES} is 4.
     *
     * @param applicationId application in the caller's tenant
     * @return one row per recorded card
     */
    @GetMapping("/{applicationId}/scorecards")
    List<ScorecardView> scorecards(@PathVariable String applicationId) {
        JobApplicationEntity application = load(applicationId);
        return this.tools.scorecards(application.getTenantId(), application.getId()).stream().map(ScorecardView::from).toList();
    }

    /**
     * Returns the single offer, or 404 when none has been stored.
     *
     * <p>A below-band allow leaves this at 404. After Bob allows {@code 190000}
     * the status is {@code EXTENDED}. After acceptance it is {@code ACCEPTED}.
     *
     * @param applicationId application in the caller's tenant
     * @return level, salary, and offer status
     */
    @GetMapping("/{applicationId}/offer")
    OfferView offer(@PathVariable String applicationId) {
        JobApplicationEntity application = load(applicationId);
        return this.tools.offer(application.getTenantId(), application.getId()).map(OfferView::from)
                .orElseThrow(SessionNotFoundException::new);
    }

    private JobApplicationEntity load(String applicationId) {
        AgentPrincipal principal = AgentPrincipal.current(this.properties);
        return this.hiring.requireApplication(principal.tenantId(), applicationId);
    }

    /** Stage read. Amounts are decimal strings because the column is {@code numeric(12,2)}. */
    public record ApplicationView(String id, String roleTitle, String stage, String bandMin, String bandMax) {
        static ApplicationView from(JobApplicationEntity entity) {
            return new ApplicationView(entity.getId(), entity.getRoleTitle(), entity.getStage().name(),
                    entity.getBandMin().toPlainString(), entity.getBandMax().toPlainString());
        }
    }

    /** One row from {@code hiring_action}. {@code action} is {@code ADVANCE}, {@code SCORECARD}, {@code OFFER_EXTENDED}, {@code OFFER_RESPONSE}, or {@code REJECT}. */
    public record ActionView(String action, String fromStage, String toStage, String reason) {
        static ActionView from(HiringActionEntity entity) {
            return new ActionView(entity.getAction(), entity.getFromStage().name(), entity.getToStage().name(), entity.getReason());
        }
    }

    /** One scorecard. The latest card for a round is the one that gates the next stage. */
    public record ScorecardView(String round, String recommendation, int score) {
        static ScorecardView from(InterviewScorecardEntity entity) {
            return new ScorecardView(entity.getRound().name(), entity.getRecommendation().name(), entity.getProblemSolving());
        }
    }

    /** Offer row. {@code status} is {@code EXTENDED}, {@code ACCEPTED}, or {@code DECLINED}. */
    public record OfferView(String level, String baseSalary, String status) {
        static OfferView from(JobOfferEntity entity) {
            return new OfferView(entity.getLevel(), entity.getBaseSalary().toPlainString(), entity.getStatus());
        }
    }
}
