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
package org.apache.qpid.messaging;

/**
 * Thrown to indicate loss of underlying connection. When
 * auto-reconnect is used this will be caught by the library and used
 * to trigger reconnection attempts. If reconnection fails (according
 * to whatever settings have been configured), then an instance of
 * this class will be thrown to signal that.
 */
public class TransportFailureException extends MessagingException
{

    public TransportFailureException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public TransportFailureException(String message)
    {
        super(message);
    }
}
