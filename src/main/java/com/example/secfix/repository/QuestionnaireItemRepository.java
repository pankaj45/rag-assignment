package com.example.secfix.repository;

import com.example.secfix.domain.QuestionnaireItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionnaireItemRepository extends JpaRepository<QuestionnaireItem, Long> {
}

