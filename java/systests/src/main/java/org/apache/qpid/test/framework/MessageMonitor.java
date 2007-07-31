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
package org.apache.qpid.test.framework;

import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * MessageMonitor is used to record information about messages received. This will provide methods to check various
 * properties, such as the type, number and content of messages received in order to verify the correct behaviour of
 * tests.
 *
 * <p/>At the moment this monitor does not do anything.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * </table>
 */
public class MessageMonitor implements MessageListener
{
    /**
     * Handles received messages. Does Nothing.
     *
     * @param message The message. Ignored.
     */
    public void onMessage(Message message)
    { }
}
