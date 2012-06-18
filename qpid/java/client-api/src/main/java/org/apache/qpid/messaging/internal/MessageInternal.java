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
package org.apache.qpid.messaging.internal;

import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.cpp.CppMessageFactory;

public interface MessageInternal extends Message
{
    /**
     * Allows Internal objects to determine if a message
     * was created by a compatible message factory without
     * having to resort to casting to find out.
     *
     * Class was used, in order to allow reuse of compatible
     * MessageFactories between implementations, instead of locking into
     * the exact instance that created it.
     *
     * @return The Class that created this Message.
     */
    public Class<? extends MessageFactory> getMessageFactoryClass();

    /**
     * Provides a reference to the Factory specific Message delegate
     * that was used when creating a concrete instance of this message.
     * You cannot assume the immediate delegate of a message
     * object to be the native format. There could be several
     * adapters and/or decorators around the native message.
     *
     * The calling Object should know how to cast the generic object
     * to the required type.
     * Ex @see {@link CppMessageFactory#CppMessageDelegate}
     * and @see {@link CppSender
     */
    public Object getFactorySpecificMessageDelegate();
}
