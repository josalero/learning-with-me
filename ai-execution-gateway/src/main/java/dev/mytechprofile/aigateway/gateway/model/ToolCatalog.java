package dev.mytechprofile.aigateway.gateway.model;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * Tool definitions sent to the model. The gateway, not the model client, decides whether a call runs.
 */
public final class ToolCatalog {

    private ToolCatalog() {}

    /** JSON schemas the live model sees. The gateway validates arguments again; the schema is only a hint. */
    public static List<ToolSpecification> specifications() {
        return List.of(
                spec(
                        "get_candidate_summary",
                        "Read status, requisition, skills, and years of experience for a candidate.",
                        JsonObjectSchema.builder()
                                .addStringProperty("candidateId")
                                .required("candidateId")
                                .build()),
                spec(
                        "search_evidence",
                        "Retrieve evidence snippets for a candidate.",
                        JsonObjectSchema.builder()
                                .addStringProperty("candidateId")
                                .addStringProperty("query")
                                .required("candidateId", "query")
                                .build()),
                spec(
                        "draft_recommendation",
                        "Store a recommendation draft that cites evidence ids.",
                        JsonObjectSchema.builder()
                                .addStringProperty("candidateId")
                                .addEnumProperty("recommendation", List.of("ADVANCE", "HOLD", "REJECT"))
                                .addStringProperty("rationale")
                                .addProperty(
                                        "citedEvidenceIds",
                                        JsonArraySchema.builder()
                                                .items(JsonStringSchema.builder().build())
                                                .build())
                                .required("candidateId", "recommendation", "rationale", "citedEvidenceIds")
                                .build()),
                spec(
                        "request_status_transition",
                        "Request a status change. This does not apply the change.",
                        JsonObjectSchema.builder()
                                .addStringProperty("candidateId")
                                .addStringProperty("from")
                                .addStringProperty("to")
                                .addStringProperty("draftId")
                                .required("candidateId", "from", "to", "draftId")
                                .build()),
                spec(
                        "execute_status_transition",
                        "Apply a status change. Not granted to screening agents.",
                        JsonObjectSchema.builder()
                                .addStringProperty("candidateId")
                                .addStringProperty("to")
                                .required("candidateId", "to")
                                .build()));
    }

    private static ToolSpecification spec(String name, String description, JsonObjectSchema parameters) {
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();
    }
}
