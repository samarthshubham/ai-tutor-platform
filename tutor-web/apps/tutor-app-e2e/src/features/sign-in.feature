@ui @smoke
Feature: Sign in and the role-based shell
  People sign in on the Keycloak page and see the navigation of their role.

  Scenario Outline: Each role signs in and lands on its home page
    Given I open "/" signed out
    Then I am on the Keycloak sign-in page
    When I sign in as "<username>" on the Keycloak page
    Then I land on "<route>"
    And the user menu shows "<name>" with role "<role>"
    And the navigation lists "<items>"

    Examples:
      | username | name         | role    | route        | items |
      | admin1   | Ada Admin    | Admin   | /admin/users | Users |
      | teacher1 | Tara Teacher | Teacher | /home        | Home  |
      | tutor1   | Uma Tutor    | Tutor   | /home        | Home  |
      | student1 | Sam Student  | Student | /home        | Home  |
      | parent1  | Pat Parent   | Parent  | /home        | Home  |

  Scenario: A deep link opened signed out returns to that link after sign-in
    Given I open "/admin/users" signed out
    When I sign in as "admin1" on the Keycloak page
    Then I land on "/admin/users"

  Scenario: A teacher is not allowed on the users screen
    Given I open "/admin/users" signed out
    When I sign in as "teacher1" on the Keycloak page
    Then I land on "/forbidden"
    And I see the heading "You don't have access"

  Scenario: Signing out ends the session and the back button shows nothing protected
    Given I open "/" signed out
    And I sign in as "teacher1" on the Keycloak page
    And I land on "/home"
    When I sign out
    Then I am on the Keycloak sign-in page
    When I go back in the browser
    Then no protected content is shown

  Scenario: API calls carry the bearer token
    Given I watch the API call "/api/me"
    And I open "/" signed out
    When I sign in as "teacher1" on the Keycloak page
    Then the watched API call carried a bearer token
