Feature: A session returns its connection
  With an action session
  As a producer or consumer
  I can retrieve the underlying connection for the session

  Scenario: The connection is closed
    Given an open session with a closed connection
    Then the connection for the session is in the closed state

  Scenario: The connection is open
    Given an open session
    Then the connection for the session is in the open state
