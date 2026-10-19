package dev.mytechprofile.aigateway.persistence;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.mytechprofile.aigateway.domain.PrincipalKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "principal")
public class PrincipalEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrincipalKind kind;

    @Column(name = "display_label", nullable = false)
    private String displayLabel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> scopes = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public PrincipalKind getKind() {
        return kind;
    }

    public List<String> getScopes() {
        return scopes;
    }
}
