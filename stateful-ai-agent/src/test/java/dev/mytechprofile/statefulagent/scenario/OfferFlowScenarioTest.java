package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.OfferResponse;

class OfferFlowScenarioTest extends ScenarioSupport {

    @Test
    void aPassingScreenReachesHiredAndANoBlocksTheOffer() {
        String id = application("tenant-a", HiringStage.RECRUITER_SCREEN, 120000, 160000);
        String recruiter = session("alice", "test-alice");
        allow("alice", "test-alice", recruiter,
                message("alice", "test-alice", recruiter, "schedule tech screen for " + id).getBody());
        allow("alice", "test-alice", recruiter,
                message("alice", "test-alice", recruiter, "tech screen for " + id + " was yes").getBody());

        String approver = session("bob", "test-bob");
        allow("bob", "test-bob", approver, message("bob", "test-bob", approver, "advance " + id + " to onsite").getBody());
        allow("bob", "test-bob", approver,
                message("bob", "test-bob", approver, "onsite for " + id + " was strong yes").getBody());
        allow("bob", "test-bob", approver, message("bob", "test-bob", approver, "advance " + id + " to offer").getBody());

        allow("alice", "test-alice", recruiter,
                message("alice", "test-alice", recruiter, "extend offer for " + id + " L4 150000").getBody());
        allow("alice", "test-alice", recruiter,
                message("alice", "test-alice", recruiter, "candidate accepted offer for " + id).getBody());

        assertThat(applications.findById(id).orElseThrow().getStage()).isEqualTo(HiringStage.HIRED);
        assertThat(offers.findByTenantIdAndApplicationId("tenant-a", id).orElseThrow().getStatus())
                .isEqualTo(OfferResponse.ACCEPTED.name());
    }

    @Test
    void aNoDebriefBlocksTheOfferAndADeclineClosesIt() {
        String blocked = application("tenant-a", HiringStage.ONSITE, 120000, 160000);
        String approver = session("bob", "test-bob");
        allow("bob", "test-bob", approver,
                message("bob", "test-bob", approver, "onsite for " + blocked + " was no").getBody());
        var offer = message("bob", "test-bob", approver, "advance " + blocked + " to offer");
        assertThat(offer.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var failed = decide("bob", "test-bob", approver, approvalId(offer.getBody()), toolCallKey(offer.getBody()),
                "ALLOW", "blocked-offer");
        assertThat(failed.getBody().get("status")).isEqualTo("FAILED");
        assertThat(applications.findById(blocked).orElseThrow().getStage()).isEqualTo(HiringStage.ONSITE);

        String declined = application("tenant-a", HiringStage.OFFER, 120000, 160000);
        String recruiter = session("alice", "test-alice");
        allow("alice", "test-alice", recruiter,
                message("alice", "test-alice", recruiter, "extend offer for " + declined + " L4 150000").getBody());
        allow("alice", "test-alice", recruiter,
                message("alice", "test-alice", recruiter, "candidate declined offer for " + declined).getBody());
        assertThat(applications.findById(declined).orElseThrow().getStage()).isEqualTo(HiringStage.OFFER_DECLINED);

        var reject = message("bob", "test-bob", approver, "reject " + declined + " bar not met");
        if (reject.getStatusCode().value() == 202) {
            var rejected = decide("bob", "test-bob", approver, approvalId(reject.getBody()), toolCallKey(reject.getBody()),
                    "ALLOW", "late-reject");
            assertThat(rejected.getBody().get("status")).isEqualTo("FAILED");
        }
        assertThat(applications.findById(declined).orElseThrow().getStage()).isEqualTo(HiringStage.OFFER_DECLINED);
    }

    @Test
    void aSalaryBelowTheBandFailsClosed() {
        String id = application("tenant-a", HiringStage.OFFER, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        var paused = message("alice", "test-alice", sessionId, "extend offer for " + id + " L4 90000");
        var failed = decide("alice", "test-alice", sessionId, approvalId(paused.getBody()),
                toolCallKey(paused.getBody()), "ALLOW", "below-band");
        assertThat(failed.getBody().get("status")).isEqualTo("FAILED");
        assertThat(String.valueOf(failed.getBody().get("answer"))).contains("OFFER_OUT_OF_BAND");
        assertThat(offers.findByTenantIdAndApplicationId("tenant-a", id)).isEmpty();
    }
}
