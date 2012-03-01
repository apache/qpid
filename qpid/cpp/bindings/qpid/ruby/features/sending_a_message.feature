Feature: Sending a message
  When working with a broker
  As a producer
  I want to send messages using an existing Sender

  Scenario: The session is closed
    Given an open session
    And creating a sender with "my-queue;{create:always}" succeeds
    And the session is closed
    Then sending the message "This is a test" should raise an error

  Scenario: The connection is closed
    Given an open session
    And creating a sender with "my-queue;{create:always}" succeeds
    And the connection is closed
    Then sending the message "This is a test" should raise an error

  Scenario: The message sends successfully
    Given an open session
    And creating a sender with "my-queue;{create:always}" succeeds
    Then sending the message "This is a test" succeeds