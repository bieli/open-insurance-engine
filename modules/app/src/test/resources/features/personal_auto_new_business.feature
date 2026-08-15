Feature: Personal Auto new business
  As a carrier underwriter
  I want submissions to go through draft, rate, underwrite, quote and bind
  So that only acceptable risks go in force and young drivers are referred

  Background:
    Given the insurance engine is running
    And a 2020 Volkswagen Golf with 15000 km per year

  Scenario: Standard Warsaw driver binds and receives declarations
    Given a Personal Auto applicant aged 23 licensed 3 years with 1 prior claim, credit Standard, region "PL-MZ"
    When the new-business workflow runs to completion
    Then the workflow path is "draft -> rate -> underwrite -> quote -> bind"
    And the policy status is "InForce"
    And the policy has a policy number
    And the rated premium is 1215.50 PLN
    When policy declarations are generated
    Then a "PolicyDeclarations" document named like "DEC-POL-" is produced

  Scenario: Driver younger than 21 is referred to underwriting
    Given a Personal Auto applicant aged 18 licensed 1 years with 0 prior claims, credit Good, region "PL-OTHER"
    When the new-business workflow runs to completion
    Then the workflow path is "draft -> rate -> underwrite -> refer"
    And the submission is referred to underwriting
    And the policy status is "Draft"

  Scenario: Preferred rest-of-Poland risk is cheaper than the Warsaw youth
    Given a Personal Auto applicant aged 35 licensed 10 years with 0 prior claims, credit Good, region "PL-OTHER"
    And a 2020 Volkswagen Golf with 12000 km per year
    When the new-business workflow runs to completion
    Then the policy status is "InForce"
    And the rated premium is 962.90 PLN
