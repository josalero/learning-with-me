package dev.mytechprofile.aigateway.gateway.authority;

import java.util.Map;

/**
 * The scope the delegating principal needs for each tool. Nobody holds {@code transition:execute}.
 */
public final class ToolScopes {

    public static final Map<String, String> REQUIRED = Map.of(
            "get_candidate_summary", "candidates:read",
            "search_evidence", "evidence:read",
            "draft_recommendation", "candidates:read",
            "request_status_transition", "transition:request",
            "execute_status_transition", "transition:execute");

    private ToolScopes() {}
}
