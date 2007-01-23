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
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;

public interface AMQProtocolWriter
{
    /**
     * Write a datablock, encoding where necessary (e.g. into a sequence of bytes)
     * @param frame the frame to be encoded and written
     */
    public void writeFrame(AMQDataBlock frame);

    public long writeRequest(int channelNum, AMQMethodBody methodBody,
                             AMQMethodListener methodListener);

    public void writeResponse(int channelNum, long requestId, AMQMethodBody methodBody);

    public void writeResponse(AMQMethodEvent evt, AMQMethodBody response);
}
