/*
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
package org.apache.qpid.disttest;

import org.apache.qpid.disttest.message.Command;

public class DistributedTestException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final Command causeCommand;

    public DistributedTestException(final String message)
    {
        this(message, (Command) null);
    }

    public DistributedTestException(final Throwable cause)
    {
        this(cause, null);
    }

    public DistributedTestException(final String message, final Throwable cause)
    {
        this(message, cause, null);
    }

    public DistributedTestException(final String message, final Command commandCause)
    {
        super(message);
        causeCommand = commandCause;
    }

    public DistributedTestException(final Throwable cause, final Command commandCause)
    {
        super(cause);
        causeCommand = commandCause;
    }

    public DistributedTestException(final String message, final Throwable cause, final Command commandCause)
    {
        super(message, cause);
        causeCommand = commandCause;
    }

    public Command getCauseCommand()
    {
        return causeCommand;
    }
}
