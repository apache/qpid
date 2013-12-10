Feature: Creating a receiver
  When working with a messaging environment
  As a consumer
  I want to create a Receiver for consuming messages

  Scenario: The session is closed
    Given a closed session
    Then creating a receiver with "my-queue" raises an exception

  Scenario: The connection is closed
    Given an open session with a closed connection
    Then creating a receiver with "my-queue" raises an exception

  Scenario: The address is malformed
    Given an open session
    Then creating a receiver with "my-queue;{foo:bar}" raises an exception

  Scenario: The address string is valid but the queue does not exist
    Given an open session
    Then creating a receiver for a nonexistent queue raises an exception

  Scenario: The address string is fine
    Given an open session
    Then creating a receiver with "my-queue;{create:always,delete:always}" succeeds

  Scenario: Using an Address object
    Given an open session
    And an Address with the string "create-receiver-test;{create:always}"
    Then creating a receiver with an Address succeeds
