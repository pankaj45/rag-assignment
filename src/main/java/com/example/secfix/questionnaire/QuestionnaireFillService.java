package com.example.secfix.questionnaire;

import org.springframework.web.multipart.MultipartFile;

public interface QuestionnaireFillService {

    QuestionnaireFillResult fill(MultipartFile questionnaire, String customerId);
}
