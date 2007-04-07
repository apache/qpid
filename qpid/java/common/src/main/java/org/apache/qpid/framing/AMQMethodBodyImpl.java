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
package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.protocol.AMQConstant;

public abstract class AMQMethodBodyImpl extends AMQBodyImpl implements AMQMethodBody
{
    public static final byte TYPE = 1;


    public abstract byte getMajor();
    public abstract byte getMinor();

    /** unsigned short */
    protected abstract int getBodySize();

    /** @return unsigned short */
    public abstract int getClazz();

    /** @return unsigned short */
    public abstract int getMethod();

    protected abstract void writeMethodPayload(ByteBuffer buffer);

    public byte getFrameType()
    {
        return TYPE;
    }

    public int getSize()
    {
        return 2 + 2 + getBodySize();
    }

    public void writePayload(ByteBuffer buffer)
    {
        EncodingUtils.writeUnsignedShort(buffer, getClazz());
        EncodingUtils.writeUnsignedShort(buffer, getMethod());
        writeMethodPayload(buffer);
    }


    /**
     * Creates an AMQChannelException for the corresponding body type (a channel exception should include the class and
     * method ids of the body it resulted from).
     */

    /**
     * Convenience Method to create a channel not found exception
     *
     * @param channelId The channel id that is not found
     *
     * @return new AMQChannelException
     */
    public AMQChannelException getChannelNotFoundException(int channelId)
    {
        return getChannelException(AMQConstant.NOT_FOUND, "Channel not found for id:" + channelId);
    }

    public AMQChannelException getChannelException(AMQConstant code, String message)
    {
        return new AMQChannelException(code, message, getClazz(), getMethod(), getMajor(), getMinor());
    }

    public AMQChannelException getChannelException(AMQConstant code, String message, Throwable cause)
    {
        return new AMQChannelException(code, message, getClazz(), getMethod(), getMajor(), getMinor(), cause);
    }

    public AMQConnectionException getConnectionException(AMQConstant code, String message)
    {
        return new AMQConnectionException(code, message, getClazz(), getMethod(), getMajor(), getMinor());
    }

    public AMQConnectionException getConnectionException(AMQConstant code, String message, Throwable cause)
    {
        return new AMQConnectionException(code, message, getClazz(), getMethod(), getMajor(), getMinor(), cause);
    }

}
