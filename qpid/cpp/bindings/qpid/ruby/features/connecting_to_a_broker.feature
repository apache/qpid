Feature: Connecting to a broker
  In order to interaction on an AMQP network
  As a producer or consumer
  I want to connect to a broker

  Scenario: Connections are closed by default
    Given a new connection
    Then the connection is in the closed state

  Scenario: Opening a connection
    Given a new connection
    And the connection is opened
    Then the connection is in the open state
