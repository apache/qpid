/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.server.exception;

/**
 * CommandInvalidException indicates that an innapropriate request has been made to a transaction manager. For example,
 * calling prepare on an already prepared transaction.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents an error due to an innapropriate request to a transction manager.
 * </table>
 */
public class CommandInvalidException extends Exception
{
    /**
     * Constructs a new CommandInvalidException with the specified detail message and
     * cause.
     *
     * @param message the detail message .
     * @param cause   the cause.
     */
    public CommandInvalidException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructs a new CommandInvalidException with the specified detail message.
     *
     * @param message the detail message.
     *
     * @deprected
     */
    public CommandInvalidException(String message)
    {
        super(message);
    }

    /**
     * Constructs a new CommandInvalidException with the specified cause.
     *
     * @param cause the cause
     *
     * @deprected
     */
    public CommandInvalidException(Throwable cause)
    {
        super(cause);
    }
}
