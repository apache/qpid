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

public abstract class AMQMethodBody extends AMQBody
{
    public static final byte TYPE = 1;

    /** AMQP version */
    protected byte major;
    protected byte minor;

    public byte getMajor()
    {
        return major;
    }

    public byte getMinor()
    {
        return minor;
    }

    public AMQMethodBody(byte major, byte minor)
    {
        this.major = major;
        this.minor = minor;
    }

    /** unsigned short */
    protected abstract int getBodySize();

    /** @return unsigned short */
    protected abstract int getClazz();

    /** @return unsigned short */
    protected abstract int getMethod();

    protected abstract void writeMethodPayload(ByteBuffer buffer);

    public byte getFrameType()
    {
        return TYPE;
    }

    protected int getSize()
    {
        return 2 + 2 + getBodySize();
    }

    protected void writePayload(ByteBuffer buffer)
    {
        EncodingUtils.writeUnsignedShort(buffer, getClazz());
        EncodingUtils.writeUnsignedShort(buffer, getMethod());
        writeMethodPayload(buffer);
    }

    protected abstract void populateMethodBodyFromBuffer(ByteBuffer buffer) throws AMQFrameDecodingException;

    protected void populateFromBuffer(ByteBuffer buffer, long size) throws AMQFrameDecodingException
    {
        populateMethodBodyFromBuffer(buffer);
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer(getClass().getName());
        buf.append("[ Class: ").append(getClazz());
        buf.append(" Method: ").append(getMethod()).append(']');
        return buf.toString();
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
        return new AMQChannelException(code, message, getClazz(), getMethod(), major, minor);
    }

    public AMQChannelException getChannelException(AMQConstant code, String message, Throwable cause)
    {
        return new AMQChannelException(code, message, getClazz(), getMethod(), major, minor, cause);
    }

    public AMQConnectionException getConnectionException(AMQConstant code, String message)
    {
        return new AMQConnectionException(code, message, getClazz(), getMethod(), major, minor);
    }

    public AMQConnectionException getConnectionException(AMQConstant code, String message, Throwable cause)
    {
        return new AMQConnectionException(code, message, getClazz(), getMethod(), major, minor, cause);
    }

}
