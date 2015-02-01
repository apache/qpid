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


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.util.BytesDataOutput;

public abstract class AMQMethodBodyImpl implements AMQMethodBody
{
    public static final byte TYPE = 1;

    public AMQMethodBodyImpl()
    {
    }

    public byte getFrameType()
    {
        return TYPE;
    }


    /** unsigned short
     *
     * @return body size*/
    abstract protected int getBodySize();


    public AMQFrame generateFrame(int channelId)
    {
        return new AMQFrame(channelId, this);
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
     * @param methodRegistry
     * @return new AMQChannelException
     */
    public AMQChannelException getChannelNotFoundException(int channelId, final MethodRegistry methodRegistry)
    {
        return getChannelException(AMQConstant.NOT_FOUND, "Channel not found for id:" + channelId, methodRegistry);
    }

    public AMQChannelException getChannelException(AMQConstant code,
                                                   String message,
                                                   final MethodRegistry methodRegistry)
    {
        return new AMQChannelException(code, message, getClazz(), getMethod(), methodRegistry);
    }

    public AMQConnectionException getConnectionException(AMQConstant code,
                                                         String message,
                                                         final MethodRegistry methodRegistry)
    {
        return new AMQConnectionException(code, message, this, methodRegistry);
    }

    public void handle(final int channelId, final AMQVersionAwareProtocolSession session) throws AMQException
    {
        session.methodFrameReceived(channelId, this);
    }

    public int getSize()
    {
        return 2 + 2 + getBodySize();
    }

    public void writePayload(DataOutput buffer) throws IOException
    {
        EncodingUtils.writeUnsignedShort(buffer, getClazz());
        EncodingUtils.writeUnsignedShort(buffer, getMethod());
        writeMethodPayload(buffer);
    }

    @Override
    public long writePayload(final ByteBufferSender sender) throws IOException
    {
        final int size = getSize();
        byte[] bytes = new byte[size];
        BytesDataOutput buffer = new BytesDataOutput(bytes);
        writePayload(buffer);
        sender.send(ByteBuffer.wrap(bytes));
        return size;
    }

    protected int getSizeOf(AMQShortString string)
    {
        return EncodingUtils.encodedShortStringLength(string);
    }

    protected void writeByte(DataOutput buffer, byte b) throws IOException
    {
        buffer.writeByte(b);
    }

    protected void writeAMQShortString(DataOutput buffer, AMQShortString string) throws IOException
    {
        EncodingUtils.writeShortStringBytes(buffer, string);
    }

    protected int readInt(DataInput buffer) throws IOException
    {
        return buffer.readInt();
    }

    protected void writeInt(DataOutput buffer, int i) throws IOException
    {
        buffer.writeInt(i);
    }

    protected int getSizeOf(FieldTable table)
    {
        return EncodingUtils.encodedFieldTableLength(table);  //To change body of created methods use File | Settings | File Templates.
    }

    protected void writeFieldTable(DataOutput buffer, FieldTable table) throws IOException
    {
        EncodingUtils.writeFieldTableBytes(buffer, table);
    }

    protected void writeLong(DataOutput buffer, long l) throws IOException
    {
        buffer.writeLong(l);
    }

    protected int getSizeOf(byte[] response)
    {
        return (response == null) ? 4 : response.length + 4;
    }

    protected void writeBytes(DataOutput buffer, byte[] data) throws IOException
    {
        EncodingUtils.writeBytes(buffer,data);
    }

    protected short readShort(DataInput buffer) throws IOException
    {
        return EncodingUtils.readShort(buffer);
    }

    protected void writeShort(DataOutput buffer, short s) throws IOException
    {
        EncodingUtils.writeShort(buffer, s);
    }

    protected void writeBitfield(DataOutput buffer, byte bitfield0) throws IOException
    {
        buffer.writeByte(bitfield0);
    }

    protected void writeUnsignedShort(DataOutput buffer, int s) throws IOException
    {
        EncodingUtils.writeUnsignedShort(buffer, s);
    }

    protected void writeUnsignedInteger(DataOutput buffer, long i) throws IOException
    {
        EncodingUtils.writeUnsignedInteger(buffer, i);
    }


    protected void writeUnsignedByte(DataOutput buffer, short unsignedByte) throws IOException
    {
        EncodingUtils.writeUnsignedByte(buffer, unsignedByte);
    }

    protected long readTimestamp(DataInput buffer) throws IOException
    {
        return EncodingUtils.readTimestamp(buffer);
    }

    protected void writeTimestamp(DataOutput buffer, long t) throws IOException
    {
        EncodingUtils.writeTimestamp(buffer, t);
    }
}
