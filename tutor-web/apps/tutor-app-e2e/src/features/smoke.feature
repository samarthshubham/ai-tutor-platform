@api @smoke
Feature: Smoke test of the running stack
  The stack started by dev.sh or the e2e workflow answers on every port,
  sign-in works and the model seam replies.

  Scenario: The API reports that it is healthy
    Given I am not signed in to the API
    When I call GET "/actuator/health"
    Then the response status is 200
    And the response field "status" is "UP"

  Scenario: The AI ping returns a reply from the fake model
    Given I am signed in to the API as "admin1"
    And the API runs the fake AI profile
    When I call GET "/api/ai/ping"
    Then the response status is 200
    And the response field "provider" is "fake"
    And the response field "reply" is not empty

  Scenario: The web app root returns the shell
    When I request the web app root
    Then the response status is 200
    And the response body contains "<tp-root>"
