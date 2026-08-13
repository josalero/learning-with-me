package dev.mytechprofile.sdlc.config;

import dev.mytechprofile.sdlc.adapter.FileArtifactStore;
import dev.mytechprofile.sdlc.adapter.InMemoryRunStore;
import dev.mytechprofile.sdlc.adapter.LocalGit;
import dev.mytechprofile.sdlc.adapter.ProcessCommandRunner;
import dev.mytechprofile.sdlc.orchestration.HumanApprovalGate;
import dev.mytechprofile.sdlc.orchestration.RunService;
import dev.mytechprofile.sdlc.orchestration.SdlcOrchestrator;
import dev.mytechprofile.sdlc.port.ArtifactStore;
import dev.mytechprofile.sdlc.port.CommandRunner;
import dev.mytechprofile.sdlc.port.RunStore;
import dev.mytechprofile.sdlc.port.VersionControlPort;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires adapters, catalogs, and the pipeline for HTTP.
 *
 * <p><strong>When to use:</strong> loaded by Spring Boot. Tests may replace {@link
 * SdlcOrchestrator} or {@link RunService}.
 *
 * <p><strong>Example:</strong> {@code CatalogLoader} is a singleton that re-reads YAML on each
 * call so dashboard creates are visible without restart.
 */
@Configuration
public class AppConfig {

    /**
     * Loads team and project YAML from {@code sdlc.home}.
     *
     * @param properties home layout
     * @return catalog loader
     */
    @Bean
    CatalogLoader catalogLoader(SdlcProperties properties) {
        return new CatalogLoader(properties);
    }

    /**
     * Fails startup with a shopping list when any team or project YAML is invalid.
     *
     * @param loader catalog
     * @return runner
     */
    @Bean
    ApplicationRunner catalogWarmup(CatalogLoader loader) {
        return args -> {
            loader.loadTeams();
            loader.loadProjects();
        };
    }

    /**
     * Writes team and project YAML from the dashboard.
     *
     * @param properties home layout
     * @param loader validator
     * @return catalog writer
     */
    @Bean
    CatalogWriter catalogWriter(SdlcProperties properties, CatalogLoader loader) {
        return new CatalogWriter(properties, loader);
    }

    /**
     * Loads role system prompts.
     *
     * @param properties home layout
     * @return prompt loader
     */
    @Bean
    PromptLoader promptLoader(SdlcProperties properties) {
        return new PromptLoader(properties.home());
    }

    /**
     * Builds OpenRouter chat models.
     *
     * @param properties API key and defaults
     * @return factory
     */
    @Bean
    ChatModelFactory chatModelFactory(SdlcProperties properties) {
        return new ChatModelFactory(properties);
    }

    /**
     * Local git adapter.
     *
     * @return git port
     */
    @Bean
    VersionControlPort versionControlPort() {
        return new LocalGit();
    }

    /**
     * Allowlisted process runner.
     *
     * @param properties isolated Gradle home
     * @return command runner
     */
    @Bean
    CommandRunner commandRunner(SdlcProperties properties) {
        return new ProcessCommandRunner(properties.gradleUserHome());
    }

    /**
     * Run artifact store under {@code runs/}.
     *
     * @param properties home layout
     * @return artifact store
     */
    @Bean
    ArtifactStore artifactStore(SdlcProperties properties) {
        return new FileArtifactStore(properties.runsDir());
    }

    /**
     * In-memory run index.
     *
     * @return run store
     */
    @Bean
    RunStore runStore() {
        return new InMemoryRunStore();
    }

    /**
     * Copies seeds into workspace.
     *
     * @param git git port
     * @return seeder
     */
    @Bean
    WorkspaceSeeder workspaceSeeder(VersionControlPort git) {
        return new WorkspaceSeeder(git);
    }

    /**
     * Builds bounded repo context for prompts.
     *
     * @return assembler
     */
    @Bean
    RepoContextAssembler repoContextAssembler() {
        return new RepoContextAssembler();
    }

    /**
     * Human stakeholder gate.
     *
     * @return gate
     */
    @Bean
    HumanApprovalGate humanApprovalGate() {
        return new HumanApprovalGate();
    }

    /**
     * Pipeline orchestrator.
     *
     * @param catalogs YAML
     * @param models OpenRouter factory
     * @param prompts system prompts
     * @param seeder workspace seeder
     * @param assembler repo context
     * @param commandRunner builds
     * @param git git
     * @param artifacts files
     * @param runs store
     * @param approvalGate HITL
     * @param properties settings
     * @return orchestrator
     */
    @Bean
    SdlcOrchestrator sdlcOrchestrator(
            CatalogLoader catalogs,
            ChatModelFactory models,
            PromptLoader prompts,
            WorkspaceSeeder seeder,
            RepoContextAssembler assembler,
            CommandRunner commandRunner,
            VersionControlPort git,
            ArtifactStore artifacts,
            RunStore runs,
            HumanApprovalGate approvalGate,
            SdlcProperties properties) {
        return new SdlcOrchestrator(
                catalogs,
                models::modelFor,
                prompts,
                seeder,
                assembler,
                commandRunner,
                git,
                artifacts,
                runs,
                approvalGate,
                properties);
    }

    /**
     * Async run facade.
     *
     * @param orchestrator pipeline
     * @param runs store
     * @param artifacts files
     * @param approvalGate HITL
     * @param properties API key check
     * @return run service
     */
    @Bean
    RunService runService(
            SdlcOrchestrator orchestrator,
            RunStore runs,
            ArtifactStore artifacts,
            HumanApprovalGate approvalGate,
            SdlcProperties properties) {
        return new RunService(orchestrator, runs, artifacts, approvalGate, properties);
    }
}
