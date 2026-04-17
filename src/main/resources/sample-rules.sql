-- Sample Classification Rules
-- These will be automatically loaded if you add them to data.sql

-- Rule 1: Mark emails from boss as urgent
INSERT INTO classification_rules (name, description, rule_type, match_condition, conditions, actions, priority, is_active) 
VALUES (
  'Boss Emails - Urgent', 
  'Mark all emails from boss as urgent',
  'IMPORTANCE',
  'ANY',
  '[{"field":"from","operator":"contains","value":"boss@company.com"}]',
  '[{"type":"set_importance","value":"URGENT"}]',
  100,
  true
);

-- Rule 2: Categorize social media emails
INSERT INTO classification_rules (name, description, rule_type, match_condition, conditions, actions, priority, is_active) 
VALUES (
  'Social Media Category', 
  'Categorize emails from social networks',
  'CATEGORIZATION',
  'ANY',
  '[{"field":"from","operator":"contains","value":"facebook.com"},{"field":"from","operator":"contains","value":"twitter.com"},{"field":"from","operator":"contains","value":"linkedin.com"}]',
  '[{"type":"set_category","value":"SOCIAL"}]',
  50,
  true
);

-- Rule 3: Mark promotional emails
INSERT INTO classification_rules (name, description, rule_type, match_condition, conditions, actions, priority, is_active) 
VALUES (
  'Promotions Detection', 
  'Identify promotional emails',
  'CATEGORIZATION',
  'ANY',
  '[{"field":"subject","operator":"contains","value":"sale"},{"field":"subject","operator":"contains","value":"discount"},{"field":"subject","operator":"contains","value":"offer"}]',
  '[{"type":"set_category","value":"PROMOTIONS"}]',
  30,
  true
);

-- Rule 4: Spam detection for specific keywords
INSERT INTO classification_rules (name, description, rule_type, match_condition, conditions, actions, priority, is_active) 
VALUES (
  'Common Spam Keywords', 
  'Detect spam based on keywords',
  'SPAM_DETECTION',
  'ANY',
  '[{"field":"subject","operator":"contains","value":"click here now"},{"field":"subject","operator":"contains","value":"you won"},{"field":"body","operator":"contains","value":"nigerian prince"}]',
  '[{"type":"mark_spam"}]',
  90,
  true
);

-- Rule 5: High priority for client emails
INSERT INTO classification_rules (name, description, rule_type, match_condition, conditions, actions, priority, is_active) 
VALUES (
  'Client Emails - High Priority', 
  'Mark client emails as high priority',
  'IMPORTANCE',
  'ANY',
  '[{"field":"from","operator":"contains","value":"@client-domain.com"}]',
  '[{"type":"set_importance","value":"HIGH"}]',
  80,
  true
);
