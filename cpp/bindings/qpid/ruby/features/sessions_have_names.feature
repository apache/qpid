Feature: Session have a name
  When using a session
  As a producer or consumer
  I can name a session and then later retrieve it by name

  Scenario: Naming a session
    Given an existing session named "test-session"
    Then the session can be retrieved by the name "test-session"
