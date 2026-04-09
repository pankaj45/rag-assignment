package com.example.secfix.web;

import com.example.secfix.questionnaire.QuestionnaireFillService;
import com.example.secfix.questionnaire.QuestionnaireFillResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/questionnaire")
@RequiredArgsConstructor
public class QuestionnaireController {

    private final QuestionnaireFillService fillService;

    @PostMapping(value = "/fill", consumes = "multipart/form-data")
    public ResponseEntity<byte[]> fill(
            @RequestParam("file") MultipartFile file,
            @RequestParam("customer_id") String customerId
    ) {
        QuestionnaireFillResult result = fillService.fill(file, customerId);

        String filename = "filled_" + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "questionnaire.xlsx");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Questionnaire-Run-Id", result.runId())
                .body(result.workbookBytes());
    }
}
