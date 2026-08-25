package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.agentic.AgenticServices;
import dev.mytechprofile.sdlc.agent.DeveloperAgent;
import dev.mytechprofile.sdlc.agent.DeveloperTools;
import dev.mytechprofile.sdlc.agent.PrReviewerAgent;
import dev.mytechprofile.sdlc.agent.ProductOwnerAgent;
import dev.mytechprofile.sdlc.agent.QaAgent;
import dev.mytechprofile.sdlc.agent.ReadOnlyRepoTools;
import dev.mytechprofile.sdlc.agent.StakeholderAgent;
import dev.mytechprofile.sdlc.agent.TechLeadAgent;
import dev.mytechprofile.sdlc.catalog.RoleSpec;
import dev.mytechprofile.sdlc.catalog.TeamPolicy;
import dev.mytechprofile.sdlc.config.PromptLoader;
import dev.mytechprofile.sdlc.domain.StateKeys;

/**
 * Builds LangChain4j agents for one run from team YAML.
 *
 * <p><strong>When to use:</strong> {@link SdlcPipelineFactory} when a role is present on the
 * team.
 *
 * <p><strong>Example:</strong> {@code factory.developer(role, context, policy)}.
 */
public final class AgentFactory {

    private final RoleModelProvider models;
    private final PromptLoader prompts;

    /**
     * Creates an agent factory.
     *
     * @param models per-role chat models
     * @param prompts system prompts
     */
    public AgentFactory(RoleModelProvider models, PromptLoader prompts) {
        this.models = models;
        this.prompts = prompts;
    }

    /**
     * Builds the Product Owner agent.
     *
     * @param role YAML role
     * @param context run handles
     * @return configured agent
     */
    public ProductOwnerAgent productOwner(RoleSpec role, RunContext context) {
        return AgenticServices.agentBuilder(ProductOwnerAgent.class)
                .chatModel(models.modelFor(role))
                .name("product-owner")
                .systemMessage(prompts.load(role.prompt()))
                .outputKey(StateKeys.FEATURE_BRIEF)
                .listener(context.listener())
                .build();
    }

    /**
     * Builds the Tech Lead agent with read-only repo tools.
     *
     * @param role YAML role
     * @param context run handles
     * @param policy tool-call cap
     * @return configured agent
     */
    public TechLeadAgent techLead(RoleSpec role, RunContext context, TeamPolicy policy) {
        return AgenticServices.agentBuilder(TechLeadAgent.class)
                .chatModel(models.modelFor(role))
                .name("tech-lead")
                .systemMessage(prompts.load(role.prompt()))
                .outputKey(StateKeys.AI_SPEC)
                .tools(new ReadOnlyRepoTools(context.workspace()))
                .maxSequentialToolsInvocations(policy.maxReadOnlyToolCalls())
                .listener(context.listener())
                .build();
    }

    /**
     * Builds the Developer agent with file tools. Tests run in {@link BuildGate}, not as a tool.
     *
     * @param role YAML role
     * @param context run handles
     * @param policy tool-call cap
     * @return configured agent
     */
    public DeveloperAgent developer(RoleSpec role, RunContext context, TeamPolicy policy) {
        DeveloperTools tools = new DeveloperTools(context.workspace());
        return AgenticServices.agentBuilder(DeveloperAgent.class)
                .chatModel(models.modelFor(role))
                .name("developer")
                .systemMessage(prompts.load(role.prompt()))
                .outputKey(StateKeys.CHANGE_SUMMARY)
                .tools(tools)
                .maxSequentialToolsInvocations(policy.maxDeveloperToolCalls())
                .listener(context.listener())
                .build();
    }

    /**
     * Builds the PR Reviewer agent.
     *
     * @param role YAML role
     * @param context run handles
     * @return configured agent
     */
    public PrReviewerAgent prReviewer(RoleSpec role, RunContext context) {
        return AgenticServices.agentBuilder(PrReviewerAgent.class)
                .chatModel(models.modelFor(role))
                .name("pr-reviewer")
                .systemMessage(prompts.load(role.prompt()))
                .outputKey(StateKeys.REVIEW_VERDICT)
                .listener(context.listener())
                .build();
    }

    /**
     * Builds the QA agent.
     *
     * @param role YAML role
     * @param context run handles
     * @return configured agent
     */
    public QaAgent qa(RoleSpec role, RunContext context) {
        return AgenticServices.agentBuilder(QaAgent.class)
                .chatModel(models.modelFor(role))
                .name("qa")
                .systemMessage(prompts.load(role.prompt()))
                .outputKey(StateKeys.QA_VERDICT)
                .listener(context.listener())
                .build();
    }

    /**
     * Builds the Stakeholder agent.
     *
     * @param role YAML role
     * @param context run handles
     * @return configured agent
     */
    public StakeholderAgent stakeholder(RoleSpec role, RunContext context) {
        return AgenticServices.agentBuilder(StakeholderAgent.class)
                .chatModel(models.modelFor(role))
                .name("stakeholder")
                .systemMessage(prompts.load(role.prompt()))
                .outputKey(StateKeys.STAKEHOLDER_DECISION)
                .listener(context.listener())
                .build();
    }
}
