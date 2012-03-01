Feature: Creating a session
  When working with a broker
  As a producer or consumer
  I want to create a session

  Scenario: The connection is closed
    Given a closed connection
    Then creating a session raises an exception

  Scenario: The connection is open
    Given an open connection
    Then creating a session works
