Feature: Closing an open connection
  When working with a broker
  As a producer or consumer
  I want to close a connection

  Scenario: The connection is already closed
    Given a closed connection
    Then calling close does not raise an exception

  Scenario: The connection is open
    Given an open connection
    And the connection is closed
    Then the connection is in the closed state