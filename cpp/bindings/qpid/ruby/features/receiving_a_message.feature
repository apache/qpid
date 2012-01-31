Feature: Receving a message
  When working with a broker
  As a message consumer
  I need to be able to receive messages

  Scenario: Receiving after the session is closed
    Given a sender and receiver for "my-queue;{create:always}"
    And the message "this is a test" is sent
    And the session is closed
    Then getting the next message raises an error

  Scenario: Receiving after the connection is closed
    Given a sender and receiver for "my-queue;{create:always}"
    And the message "this is a test" is sent
    And the connection is closed
    Then getting the next message raises an error

  Scenario: No message is received on an empty queue
    Given an existing receiver for "my-queue;{create:always}"
    And the receiver has no pending messages
    Then getting the next message raises an error

  Scenario: A message is pending
    Given a sender and receiver for "my-queue;{create:always}"
    And the receiver has a capacity of 1
    And the message "this is a test" is sent
    Then the receiver should have 1 message available
    And the receiver should receive a message with "this is a test"
