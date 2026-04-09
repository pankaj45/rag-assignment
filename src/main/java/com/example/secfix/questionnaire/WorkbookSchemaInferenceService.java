package com.example.secfix.questionnaire;

import com.example.secfix.questionnaire.schema.WorkbookSchema;
import com.example.secfix.questionnaire.schema.WorkbookSnapshot;

public interface WorkbookSchemaInferenceService {

    WorkbookSchema infer(WorkbookSnapshot snapshot);
}
