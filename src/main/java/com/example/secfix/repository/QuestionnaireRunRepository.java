package com.example.secfix.repository;

import com.example.secfix.domain.QuestionnaireRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireRunRepository extends JpaRepository<QuestionnaireRun, Long> {

    Optional<QuestionnaireRun> findByRunId(String runId);

    List<QuestionnaireRun> findAllByOrderByCreatedAtDesc();

    Optional<QuestionnaireRun> findByRunIdAndCustomerId(String runId, String customerId);

    List<QuestionnaireRun> findAllByCustomerIdOrderByCreatedAtDesc(String customerId);
}
