Feature: Grade 8 demo school

  Scenario: Prepare the Grade 8 classroom
    Given the nine demo users have signed in
    And the demo parents and tutor are linked to their students
    And the two teachers have their courses, topics and enrolled students
    When the teachers and administrator prepare the five lessons
    Then the seed prints the demo school counts
