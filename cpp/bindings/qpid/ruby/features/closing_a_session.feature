Feature: Closing an open session
  While working with a session
  As a producer or consumer
  I want to close the session

  Scenario: The connection has already been closed
    Given an open session with a closed connection
    Then closing the session does not raise an error

  Scenario: Closing an active session
    Given an open session
    Then closing the session does not raise an error
    And the connection is in the open state
