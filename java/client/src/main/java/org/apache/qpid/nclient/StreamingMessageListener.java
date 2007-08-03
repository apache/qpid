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

import org.apache.qpidity.Header;

/**
 * <p>This message listener is useful if you need to
 * know when each message part becomes available
 * as opposed to knowing when the whole message arrives.</p>
 * <p/>
 * <p> The sequence of event for transferring a message is as follows:
 * <ul>
 * <li> n calls to addMessageHeaders (should be usually one or two)
 * <li> n calls to addData
 * <li> {@link org.apache.qpid.nclient.MessageListener#messageTransfer}(<code>null</code>).
 * </ul>
 * This is up to the implementation to assembled the message when the different parts
 * are transferred.
 */
public interface StreamingMessageListener extends MessageListener
{
    /**
     * Add the following headers ( {@link org.apache.qpidity.DeliveryProperties}
     * or {@link org.apache.qpidity.ApplicationProperties} ) to the message being received.
     *
     * @param headers Either <code>DeliveryProperties</code> or <code>ApplicationProperties</code>
     */
    public void addMessageHeaders(Header... headers);

    /**
     * Add the following byte array to the content of the message being received
     *
     * @param data Data to be added or streamed.
     */
    public void addData(byte[] data);

}
