Feature: Billing and documents after bind
  As a billing clerk
  I want written premium to be invoiced and forms to be issued at bind
  So that cash application matches the rate and the customer has a declarations page

  Background:
    Given the insurance engine is running
    And a 2020 Volkswagen Golf with 15000 km per year
    And a Personal Auto applicant aged 23 licensed 3 years with 1 prior claim, credit Standard, region "PL-MZ"
    When the new-business workflow runs to completion

  Scenario: Quarterly invoices reconcile to written premium
    When quarterly invoices are created for the bound policy
    Then 4 invoices exist
    And the invoices sum to the rated premium
    When all invoices are billed and paid in full
    Then every invoice status is "Paid"

  Scenario: Bind produces a declarations page for the customer
    When policy declarations are generated
    Then a "PolicyDeclarations" document named like "DEC-POL-" is produced
    And the declarations mention the rated premium
