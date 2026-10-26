package dev.mytechprofile.statefulagent.api;

import java.util.List;
import java.util.UUID;

/**
 * Body of a message or decision response.
 *
 * <p>{@code status} is {@code COMPLETED}, {@code AWAITING_APPROVAL}, {@code DENIED},
 * or {@code FAILED}. {@code approval} is null when the turn did not pause.
 *
 * <p><strong>Example:</strong> denying a schedule returns
 * {@code {"status":"DENIED","approval":{"status":"DENIED"}}}.
 */
public record TurnResult(String status, String answer, ApprovalView approval) {

    /** Finished turn with a model answer and no open approval. */
    public static TurnResult completed(String answer) {
        return new TurnResult("COMPLETED", answer, null);
    }

    /** Pause. The HTTP layer maps this status to 202. */
    public static TurnResult awaiting(ApprovalView approval) {
        return new TurnResult("AWAITING_APPROVAL", null, approval);
    }

    /** Human deny, or a tool the caller is not allowed to invoke. */
    public static TurnResult denied(String answer, ApprovalView approval) {
        return new TurnResult("DENIED", answer, approval);
    }

    /** The tool ran and a hiring rule rejected it, such as {@code OFFER_OUT_OF_BAND}. */
    public static TurnResult failed(String answer, ApprovalView approval) {
        return new TurnResult("FAILED", answer, approval);
    }

    /** Open or finished approval. {@code id} is the path value for the decision call. */
    public record ApprovalView(UUID id, String status, String risk, List<CallView> calls) {
    }

    /** One paused call. Send {@code toolCallKey} back on the decision. */
    public record CallView(String toolCallKey, String toolName, String decision) {
    }
}
