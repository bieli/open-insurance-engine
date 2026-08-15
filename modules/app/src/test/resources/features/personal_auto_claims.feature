Feature: Personal Auto claims handling
  As a claims handler
  I want FNOL, reserve, payment and denial to follow policy and catalog rules
  So that we do not pay invalid losses and high-severity claims are escalated

  Background:
    Given the insurance engine is running
    And a 2020 Volkswagen Golf with 15000 km per year
    And a Personal Auto applicant aged 23 licensed 3 years with 1 prior claim, credit Standard, region "PL-MZ"

  Scenario: Collision FNOL on an in-force policy is reserved, paid and closed
    When the new-business workflow runs to completion
    And a collision FNOL is opened with tier "Medium" and description "Rear-end collision in Warsaw"
    And a vehicle_damage reserve of 8500.00 PLN is set
    And the claim is approved
    And an indemnity of 7500.00 PLN is paid
    And the claim is closed
    Then the claim status is "Closed"
    And the claim has a claim number
    And total paid is 7500.00 PLN
    When a claim acknowledgement is generated
    Then a "ClaimAcknowledgement" document named like "ACK-CLM-" is produced

  Scenario: FNOL is rejected when the policy is not in force
    When the new-business workflow runs to completion
    And the in-force policy is cancelled
    And a collision FNOL is opened with tier "Medium" and description "Loss after cancel"
    Then the last operation failed with code "RULE_CLM_POLICY_INFORCE"

  Scenario: Reserve above the coverage limit is rejected at FNOL
    When the new-business workflow runs to completion
    And the FNOL will include an initial reserve of 1000001.00 PLN
    And a collision FNOL is opened with tier "Medium" and description "Total loss"
    Then the last operation failed with code "RULE_CLM_RESERVE_LIMIT"

  Scenario: High-severity FNOL is referred to specialist handling
    When the new-business workflow runs to completion
    And a collision FNOL is opened with tier "High" and description "Multi-vehicle pile-up"
    Then the claim status is "UnderInvestigation"
    And the claim has a claim number

  Scenario: Claim is denied after FNOL
    When the new-business workflow runs to completion
    And a collision FNOL is opened with tier "Low" and description "Glass chip, not covered"
    And the claim is denied because "Glass not on policy"
    Then the claim status is "Denied"
