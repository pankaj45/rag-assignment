package com.example.secfix.web;

import com.example.secfix.domain.QuestionnaireRun;
import com.example.secfix.repository.QuestionnaireRunRepository;
import com.example.secfix.web.dto.QuestionnaireRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questionnaire/runs")
@RequiredArgsConstructor
public class QuestionnaireRunController {

    private final QuestionnaireRunRepository questionnaireRunRepository;

    @GetMapping
    public List<QuestionnaireRunResponse> listRuns(@RequestParam("customer_id") String customerId) {
        return questionnaireRunRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{runId}")
    public ResponseEntity<QuestionnaireRunResponse> getRun(
            @PathVariable String runId,
            @RequestParam("customer_id") String customerId
    ) {
        return questionnaireRunRepository.findByRunIdAndCustomerId(runId, customerId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private QuestionnaireRunResponse toResponse(QuestionnaireRun run) {
        return new QuestionnaireRunResponse(
                run.getRunId(),
                run.getCustomerId(),
                run.getStatus(),
                run.getInputFileName(),
                run.getOutputFileName(),
                run.getTotalFields(),
                run.getAnsweredFields(),
                run.getInsufficientContextFields(),
                run.getFailedFields(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }
}
