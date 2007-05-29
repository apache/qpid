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
package org.apache.qpid.client.failover;

/**
 * This exception is thrown when failover is taking place and otherparts of the client need to know about this.
 *
 * @todo This exception is created and passed as an argument to a method, rather than thrown. The exception is being
 *       used to represent a signal, passed out to other threads. Use of exceptions as arguments rather than as
 *       exceptions is extremly confusing. Eliminate. Use a Condition or set a flag and check it instead. Also
 *       FailoverException is Runtime but handled and should only use Runtimes for non-handleable conditions.
 */
public class FailoverException extends RuntimeException
{
    public FailoverException(String message)
    {
        super(message);
    }
}
