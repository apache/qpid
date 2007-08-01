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
 */
package org.apache.qpid.nclient.api;

import java.util.Set;

import org.apache.qpidity.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.api.Message;

/**
 * Used to receive messages from a queue
 */
public interface MessageReceiver
{
    /**
     * Get this receiver options.
     *
     * @return This receiver set of options.
     */
	
    public Set<Option> getOptions();

    /**
     * Receive a message form this receiver queue.
     * <p> If the timeout is equal to 0 then this operation is blocking.
     * <p> If the timeout is less than 0 then this operation retruns immediatly
     * <p> Otherwise it blocks until a message arrives, the timeout expires, or this receiver is closed.
     * <p> To receive messages, a receiver must be started.
     *
     * @param timeout The timeout value (in milliseconds).
     * @return A message or null if timeout expires or this receiver is concurrently closed.
     * @throws QpidException         If this receiver fails to receive a message due to some error.
     * @throws IllegalStateException If this receiver is closed, not started or a MessageListener is set.
     */
    public Message receive(long timeout)
            throws
            QpidException,
            IllegalStateException;

    /**
     * Stop the delivery of messages to this receiver.
     * <p>For asynchronous receiver, this operation blocks until the message listener
     * finishes processing the current message,
     *
     * @throws QpidException         If this receiver fails to be stopped due to some error.
     * @throws IllegalStateException If this receiver is closed or already stopped.
     */
    public void stop()
            throws
            QpidException,
            IllegalStateException;

    /**
     * Start the delivery of messages to this receiver.
     *
     * @throws QpidException         If this receiver fails to be started due to some error.
     * @throws IllegalStateException If this receiver is closed or already started.
     */
    public void start()
            throws
            QpidException,
            IllegalStateException;

    /**
     * Set the receiver�s MessageListener.
     * Setting the message listener to null is the equivalent of un-setting the message
     * listener for this receiver.
     * <p> Once a message listener is set, a receiver cannot receive messages through its
     * receive method.
     *
     * @param listener The message listner.
     * @throws QpidException         If this receiver fails to set the message listner due to some error.
     * @throws IllegalStateException If this receiver is closed.
     */
    public void setAsynchronous(MessageListener listener)
            throws
            QpidException,
            IllegalStateException;
}
