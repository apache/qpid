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
package org.apache.qpid.server.cluster.util;

import java.text.MessageFormat;

/**
 * Convenience class to allow log messages to be specified in terms
 * of MessageFormat patterns with a variable set of parameters. The
 * production of the string is only done if toSTring is called so it
 * works well with debug level messages, allowing complex messages
 * to be specified that are only evaluated if actually printed.
 *
 */
public class LogMessage
{
    private final String _message;
    private final Object[] _args;

    public LogMessage(String message)
    {
        this(message, new Object[0]);
    }

    public LogMessage(String message, Object... args)
    {
        _message = message;
        _args = args;
    }

    public String toString()
    {
        return MessageFormat.format(_message, _args);
    }
}
