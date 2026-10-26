package dev.mytechprofile.statefulagent.platform;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.mytechprofile.statefulagent.business.HiringStage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties(prefix = "agent")
@Validated
public class AgentProperties {

    @Valid
    @NotNull
    private final Security security = new Security();

    @Valid
    @NotNull
    private final Risk risk = new Risk();

    @Valid
    @NotNull
    private final Session session = new Session();

    @Valid
    @NotNull
    private final Approval approval = new Approval();

    public Security getSecurity() {
        return this.security;
    }

    public Risk getRisk() {
        return this.risk;
    }

    public Session getSession() {
        return this.session;
    }

    public Approval getApproval() {
        return this.approval;
    }

    public UserAccount requireUser(String username) {
        return this.security.users.stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not configured"));
    }

    public static class Security {

        @Valid
        @NotEmpty
        private List<UserAccount> users = new ArrayList<>();

        public List<UserAccount> getUsers() {
            return this.users;
        }

        public void setUsers(List<UserAccount> users) {
            this.users = users;
        }
    }

    public static class UserAccount {

        @NotBlank
        private String username;

        @NotBlank
        private String password;

        @NotBlank
        private String tenant;

        @NotEmpty
        private List<String> roles = new ArrayList<>();

        public String getUsername() {
            return this.username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return this.password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getTenant() {
            return this.tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }

        public List<String> getRoles() {
            return this.roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }

    public static class Risk {

        @NotEmpty
        private List<HiringStage> highStages = new ArrayList<>(List.of(HiringStage.ONSITE, HiringStage.OFFER));

        public List<HiringStage> getHighStages() {
            return this.highStages;
        }

        public void setHighStages(List<HiringStage> highStages) {
            this.highStages = highStages;
        }
    }

    public static class Session {

        @Valid
        @NotNull
        private final Compaction compaction = new Compaction();

        public Compaction getCompaction() {
            return this.compaction;
        }
    }

    public static class Compaction {

        @Positive
        private int maxTurns = 20;

        @Positive
        private int maxTokens = 8000;

        @Positive
        private int preserveTurns = 8;

        public int getMaxTurns() {
            return this.maxTurns;
        }

        public void setMaxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
        }

        public int getMaxTokens() {
            return this.maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getPreserveTurns() {
            return this.preserveTurns;
        }

        public void setPreserveTurns(int preserveTurns) {
            this.preserveTurns = preserveTurns;
        }
    }

    public static class Approval {

        @NotNull
        private NewInputPolicy onNewInput = NewInputPolicy.REJECT;

        @NotNull
        private Duration executionLease = Duration.ofSeconds(30);

        @Positive
        private long recoveryDelayMs = 15_000;

        public NewInputPolicy getOnNewInput() {
            return this.onNewInput;
        }

        public void setOnNewInput(NewInputPolicy onNewInput) {
            this.onNewInput = onNewInput;
        }

        public Duration getExecutionLease() {
            return this.executionLease;
        }

        public void setExecutionLease(Duration executionLease) {
            this.executionLease = executionLease;
        }

        public long getRecoveryDelayMs() {
            return this.recoveryDelayMs;
        }

        public void setRecoveryDelayMs(long recoveryDelayMs) {
            this.recoveryDelayMs = recoveryDelayMs;
        }
    }

    public enum NewInputPolicy {
        REJECT,
        AUTO_DENY
    }
}
