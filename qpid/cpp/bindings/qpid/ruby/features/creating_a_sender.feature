Feature: Creating a sender
  When working with a session
  As a producer
  I want to create a Sender for sending messages

  Scenario: The session is closed
    Given a closed session
    Then creating a sender with "my-queue;{create:always,delete:always}" raises an exception

  Scenario: The connection is closed
    Given an open session with a closed connection
    Then creating a sender with "my-queue;{create:always,delete:always}" raises an exception

  Scenario: The address is malformed
    Given an open session
    Then creating a sender with "my-queue;{foo:bar}" raises an exception

  Scenario: The address string is valid
    Given an open session
    Then creating a sender with "my-queue;{create:always,delete:always}" succeeds

  Scenario: Using an Address object
    Given an open session
    And an Address with the uri "my-queue/my-subject;{create:always}"
    Then creating a sender with an Address succeeds
