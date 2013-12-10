Feature: Getting the authenticated username from an open connection.
  When connected to a broker
  As a producer or consumer
  I can retrieve the username used to authenticate

  Scenario: When connected anonymously
    Given an open connection
    Then the authenticated username should be "anonymous"
