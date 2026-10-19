package dev.mytechprofile.aigateway.authority;

import java.util.Map;

public final class ToolScopes {

    public static final Map<String, String> REQUIRED = Map.of(
            "get_candidate_summary", "candidates:read",
            "search_evidence", "evidence:read",
            "draft_recommendation", "candidates:read",
            "request_status_transition", "transition:request",
            "execute_status_transition", "transition:execute");

    private ToolScopes() {}
}
