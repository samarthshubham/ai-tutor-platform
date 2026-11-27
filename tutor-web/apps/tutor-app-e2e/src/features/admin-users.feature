@ui
Feature: Administering users and their links
  An admin lists users, links a parent to a student and removes the link again.

  Background:
    Given the demo users have signed in once
    And "student1" has no guardian links

  Scenario: An admin links a parent to a student and removes the link
    Given I am signed in as "admin1"
    And I open "/admin/users"
    When I filter users by role "Parent"
    Then the users table lists "Pat Parent"
    And the users table does not list "Sam Student"
    When I filter users by role "Student"
    And I open the links of "Sam Student"
    And I add "Pat Parent" under "Parents and guardians"
    Then "Parents and guardians" lists "Pat Parent"
    When I remove "Pat Parent" from the links
    Then "Parents and guardians" lists nobody
