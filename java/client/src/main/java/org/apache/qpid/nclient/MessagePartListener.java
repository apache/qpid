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
package org.apache.qpid.nclient;

import java.nio.ByteBuffer;

import org.apache.qpid.transport.Header;

/**
 * Assembles message parts.
 * <p> The sequence of event for transferring a message is as follows:
 * <ul>
 * <li> messageHeaders
 * <li> n calls to addData
 * <li> messageReceived
 * </ul>
 * It is up to the implementation to assemble the message once the different parts
 * are transferred.
 */
public interface MessagePartListener
{    
    /**
     * Indicates the Message transfer has started.
     * 
     * @param transferId The message transfer ID. 
     */
    public void messageTransfer(int transferId);
    
    /**
     * Add the following a header to the message being received.
     *
     * @param header Either <code>DeliveryProperties</code> or <code>ApplicationProperties</code>
     */
    public void messageHeader(Header header);

    /**
     * Add the following byte array to the content of the message being received
     *
     * @param src Data to be added or streamed.
     */
    public void data(ByteBuffer src);

    /**
     * Indicates that the message has been fully received. 
     */
    public void messageReceived();

}
