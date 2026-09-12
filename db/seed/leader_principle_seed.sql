-- ────────────────────────────────────────────────────────────────────────────
-- Gita Leader — principle names, keyed by code (P01..P50).
-- Source: HEAIL_GitaLeader_QuestionBank_500_TOUGH.xlsx (Principle No -> Principle).
-- Read by AssessmentService.toResponse() for strongest/weakest principle text.
-- Hibernate (ddl-auto=update) creates the table from the LeaderPrinciple entity;
-- the CREATE below is belt-and-braces for running this by hand. Safe to re-run.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS leader_principle (
    code varchar(3) PRIMARY KEY,
    name text        NOT NULL
);

INSERT INTO leader_principle (code, name) VALUES
  ('P01', 'Focus on Action, Not Outcome'),
  ('P02', 'Lead by Example'),
  ('P03', 'Know Your People''s Dharma'),
  ('P04', 'Conquer Fear Before Leading'),
  ('P05', 'Higher Purpose Beyond Profit'),
  ('P06', 'The Leader Must Know the Self'),
  ('P07', 'Clarity of Vision Cuts Through Chaos'),
  ('P08', 'Situational Awareness'),
  ('P09', 'Inspire Through Meaning'),
  ('P10', 'Equanimity — The Unshakeable Leader'),
  ('P11', 'Detached Decision-Making'),
  ('P12', 'Long-Term Thinking'),
  ('P13', 'Act with Intelligence, Not Just Speed'),
  ('P14', 'Seek Counsel'),
  ('P15', 'Know What to Fight and What to Surrender'),
  ('P16', 'Adapt Strategy — Flexibility'),
  ('P17', 'First Principles Thinking'),
  ('P18', 'Distinguish Urgency from Importance'),
  ('P19', 'Data + Intuition'),
  ('P20', 'Competitive Intelligence'),
  ('P21', 'Complementary Strengths'),
  ('P22', 'Psychological Safety'),
  ('P23', 'Delegation as Trust'),
  ('P24', 'Resolve Conflict at its Root'),
  ('P25', 'Recognise Contribution'),
  ('P26', 'Build a Culture of Continuous Learning'),
  ('P27', 'Instil Dharma — Values Over Rules'),
  ('P28', 'Mentor Relationships'),
  ('P29', 'Right Communication at the Right Time'),
  ('P30', 'Celebrate Failure as Data'),
  ('P31', 'Master Your Emotions'),
  ('P32', 'Discipline Over Motivation'),
  ('P33', 'Manage the Six Enemies of Leadership'),
  ('P34', 'Detachment from Title and Status'),
  ('P35', 'Wellbeing as Strategy'),
  ('P36', 'The Power of Concentration'),
  ('P37', 'Respond, Don''t React'),
  ('P38', 'Learn to Ask, Not Just Tell'),
  ('P39', 'Transcend Comparison'),
  ('P40', 'Develop Witness Consciousness'),
  ('P41', 'Integrity Is Non-Negotiable'),
  ('P42', 'Serve the Customer as Your Dharma'),
  ('P43', 'Resilience — Rise After Every Fall'),
  ('P44', 'Stakeholder Harmony'),
  ('P45', 'Do What Is Right, Even When Hard'),
  ('P46', 'Build Systems That Outlast You'),
  ('P47', 'Sustainable Growth'),
  ('P48', 'Navigate Ambiguity with Grace'),
  ('P49', 'The Organisation as a Living Organism'),
  ('P50', 'Legacy — The Ultimate Leadership Metric')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
