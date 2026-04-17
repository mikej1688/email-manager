package com.emailmanager.repository;

import com.emailmanager.entity.ClassificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, Long> {

    List<ClassificationRule> findByIsActiveTrueOrderByPriorityDesc();

    List<ClassificationRule> findByRuleTypeAndIsActiveTrue(ClassificationRule.RuleType ruleType);
}
