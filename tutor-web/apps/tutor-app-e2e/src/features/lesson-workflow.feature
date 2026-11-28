@ui
Feature: Author, review and read a lesson
  Scenario: A teacher writes a draft, gets feedback and an enrolled student reads it after publishing
    Given "student1" has registered with the tutor app
    And I am signed in as "teacher1"
    When I create a course for this lesson
    And I add a topic and enroll "Sam Student"
    And I write and reload the lesson draft
    And I submit the lesson for review
    And I am signed in as "admin1"
    And I send the lesson back for changes
    And I am signed in as "teacher1"
    And I revise the draft using the review comment
    And I submit the lesson for review
    And I am signed in as "admin1"
    And I publish the reviewed lesson
    And I am signed in as "student1"
    Then I can read the published lesson in my course
