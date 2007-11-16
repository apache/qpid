/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.test.framework;

/**
 * A Publisher is a {@link CircuitEnd} that represents the status of the publishing side of a test circuit. Its main
 * purpose is to provide assertions that can be applied to test the behaviour of the publishers.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Provide assertion that the publishers received no exceptions.
 * <tr><td> Provide assertion that the publishers received a no consumers error code on every message.
 * <tr><td> Provide assertion that the publishers received a no route error code on every message.
 * </table>
 */
public interface Publisher
{
    /**
     * Provides an assertion that the publisher encountered no exceptions.
     *
     * @return An assertion that the publisher encountered no exceptions.
     */
    public Assertion noExceptionsAssertion();

    /**
     * Provides an assertion that the publisher got a no consumers exception on every message.
     *
     * @return An assertion that the publisher got a no consumers exception on every message.
     */
    public Assertion noConsumersAssertion();

    /**
     * Provides an assertion that the publisher got a no rout exception on every message.
     *
     * @return An assertion that the publisher got a no rout exception on every message.
     */
    public Assertion noRouteAssertion();
}
