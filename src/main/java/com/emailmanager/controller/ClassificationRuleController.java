package com.emailmanager.controller;

import com.emailmanager.entity.ClassificationRule;
import com.emailmanager.repository.ClassificationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for classification rules
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ClassificationRuleController {

    private final ClassificationRuleRepository ruleRepository;

    /**
     * Get all classification rules
     */
    @GetMapping
    public ResponseEntity<List<ClassificationRule>> getAllRules() {
        List<ClassificationRule> rules = ruleRepository.findAll();
        return ResponseEntity.ok(rules);
    }

    /**
     * Get active classification rules
     */
    @GetMapping("/active")
    public ResponseEntity<List<ClassificationRule>> getActiveRules() {
        List<ClassificationRule> rules = ruleRepository.findByIsActiveTrueOrderByPriorityDesc();
        return ResponseEntity.ok(rules);
    }

    /**
     * Get classification rule by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClassificationRule> getRuleById(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new classification rule
     */
    @PostMapping
    public ResponseEntity<ClassificationRule> createRule(@RequestBody ClassificationRule rule) {
        ClassificationRule savedRule = ruleRepository.save(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRule);
    }

    /**
     * Update classification rule
     */
    @PutMapping("/{id}")
    public ResponseEntity<ClassificationRule> updateRule(
            @PathVariable Long id,
            @RequestBody ClassificationRule rule) {

        return ruleRepository.findById(id)
                .map(existingRule -> {
                    rule.setId(id);
                    ClassificationRule updated = ruleRepository.save(rule);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete classification rule
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    ruleRepository.delete(rule);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Activate rule
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<ClassificationRule> activateRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setIsActive(true);
                    ClassificationRule updated = ruleRepository.save(rule);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deactivate rule
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ClassificationRule> deactivateRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setIsActive(false);
                    ClassificationRule updated = ruleRepository.save(rule);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
