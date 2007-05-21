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
 * General purpose exception for non-specifc error cases. Do not use.
 *
 * @deprected Far too broad to be a checked exception. Will be abused as a "don't know what to do with it" exception
 *            when Runtimes should be used. If this has a specific meaning within transaction managers, it should
 *            be renamed to something like TxManagerException, for example. At the moment, transaction managers are not
 *            catching this exception and taking some action, so it is clear that it is being used for errors that are
 *            not recoverable/handleable from; use runtimes. So far, it is only caught to be rethrown as AMQException,
 *            which is the other catch-all exception case to be eliminated. There are sequences in the code where
 *            AMQException is caught and rethrown as InternalErrorException, which is cause and rethrown as AMQException.
 */
public class InternalErrorException extends Exception
{
    /**
     * Constructs a new InternalErrorException with the specified detail message.
     *
     * @param message the detail message.
     *
     * @deprected
     */
    public InternalErrorException(String message)
    {
        super(message);
    }

    /**
     * Constructs a new InternalErrorException with the specified detail message and
     * cause.
     *
     * @param message the detail message .
     * @param cause   the cause.
     */
    public InternalErrorException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructs a new InternalErrorException with the specified cause.
     *
     * @param cause the cause
     *
     * @deprected
     */
    public InternalErrorException(Throwable cause)
    {
        super(cause);
    }
}
