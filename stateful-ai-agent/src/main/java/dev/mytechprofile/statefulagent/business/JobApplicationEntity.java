package dev.mytechprofile.statefulagent.business;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_application")
public class JobApplicationEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "role_title", nullable = false)
    private String roleTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HiringStage stage;

    @Column(name = "band_min", nullable = false)
    private BigDecimal bandMin;

    @Column(name = "band_max", nullable = false)
    private BigDecimal bandMax;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRoleTitle() {
        return this.roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public HiringStage getStage() {
        return this.stage;
    }

    public void setStage(HiringStage stage) {
        this.stage = stage;
    }

    public BigDecimal getBandMin() {
        return this.bandMin;
    }

    public void setBandMin(BigDecimal bandMin) {
        this.bandMin = bandMin;
    }

    public BigDecimal getBandMax() {
        return this.bandMax;
    }

    public void setBandMax(BigDecimal bandMax) {
        this.bandMax = bandMax;
    }
}
