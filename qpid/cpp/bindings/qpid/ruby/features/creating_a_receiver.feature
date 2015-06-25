# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.


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
