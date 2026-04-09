package com.example.secfix.repository;

import com.example.secfix.domain.QuestionnaireField;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionnaireFieldRepository extends JpaRepository<QuestionnaireField, Long> {
}

