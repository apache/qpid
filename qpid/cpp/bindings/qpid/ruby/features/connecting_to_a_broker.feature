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
