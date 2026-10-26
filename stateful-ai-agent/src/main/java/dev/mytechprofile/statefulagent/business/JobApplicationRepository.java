package dev.mytechprofile.statefulagent.business;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplicationEntity, String> {
}
