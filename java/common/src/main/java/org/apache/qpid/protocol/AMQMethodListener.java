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
package org.apache.qpid.protocol;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;

/**
 * Interface that allows classes to register for interest in protocol method frames.
 * 
 */
public interface AMQMethodListener
{
    /**
     * Invoked when a method frame has been received
     * @param evt the event that contains the method and channel
     * @param protocolSession the protocol session associated with the event
     * @return true if the handler has processed the method frame, false otherwise. Note
     * that this does not prohibit the method event being delivered to subsequent listeners
     * but can be used to determine if nobody has dealt with an incoming method frame.
     * @throws AMQException if an error has occurred. This exception will be delivered
     * to all registered listeners using the error() method (see below) allowing them to
     * perform cleanup if necessary.
     */
    <B extends AMQMethodBody> boolean methodReceived(AMQMethodEvent<B> evt) throws Exception;

    /**
     * Callback when an error has occurred. Allows listeners to clean up.
     * @param e
     */
    void error(Exception e);
}
