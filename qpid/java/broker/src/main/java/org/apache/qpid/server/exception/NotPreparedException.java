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
 * NotPreparedException indicates a failure to commit a transaction that has not been prepared.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents failure to commit an unprepared transaction.
 * </table>
 *
 * @todo There is already a CommandInvalidException which would seem to cover this too. Use it instead?
 */
public class NotPreparedException extends Exception
{
    /**
     * Constructs a new NotPreparedException with the specified detail message.
     *
     * @param message the detail message.
     */
    public NotPreparedException(String message)
    {
        super(message);
    }

    /**
     * Constructs a new NotPreparedException with the specified detail message and
     * cause.
     *
     * @param message the detail message .
     * @param cause   the cause.
     *
     * @deprecated
     */
    public NotPreparedException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructs a new NotPreparedException with the specified cause.
     *
     * @param cause the cause
     *
     * @deprected
     */
    public NotPreparedException(Throwable cause)
    {
        super(cause);
    }
}
