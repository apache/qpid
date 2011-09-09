package org.apache.qpid.framing;

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

import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

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


    /** unsigned short */
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
     * @return new AMQChannelException
     */
    public AMQChannelException getChannelNotFoundException(int channelId)
    {
        return getChannelException(AMQConstant.NOT_FOUND, "Channel not found for id:" + channelId);
    }

    public AMQChannelException getChannelException(AMQConstant code, String message)
    {
        return new AMQChannelException(code, message, getClazz(), getMethod(), getMajor(), getMinor(), null);
    }

    public AMQChannelException getChannelException(AMQConstant code, String message, Throwable cause)
    {
        return new AMQChannelException(code, message, getClazz(), getMethod(), getMajor(), getMinor(), cause);
    }

    public AMQConnectionException getConnectionException(AMQConstant code, String message)
    {
        return new AMQConnectionException(code, message, getClazz(), getMethod(), getMajor(), getMinor(), null);
    }

    public AMQConnectionException getConnectionException(AMQConstant code, String message, Throwable cause)
    {
        return new AMQConnectionException(code, message, getClazz(), getMethod(), getMajor(), getMinor(), cause);
    }

    public void handle(final int channelId, final AMQVersionAwareProtocolSession session) throws AMQException
    {
        session.methodFrameReceived(channelId, this);
    }

    public int getSize()
    {
        return 2 + 2 + getBodySize();
    }

    public void writePayload(DataOutputStream buffer) throws IOException
    {
        EncodingUtils.writeUnsignedShort(buffer, getClazz());
        EncodingUtils.writeUnsignedShort(buffer, getMethod());
        writeMethodPayload(buffer);
    }


    protected byte readByte(DataInputStream buffer) throws IOException
    {
        return buffer.readByte();
    }

    protected AMQShortString readAMQShortString(DataInputStream buffer) throws IOException
    {
        return EncodingUtils.readAMQShortString(buffer);
    }

    protected int getSizeOf(AMQShortString string)
    {
        return EncodingUtils.encodedShortStringLength(string);
    }

    protected void writeByte(DataOutputStream buffer, byte b) throws IOException
    {
        buffer.writeByte(b);
    }

    protected void writeAMQShortString(DataOutputStream buffer, AMQShortString string) throws IOException
    {
        EncodingUtils.writeShortStringBytes(buffer, string);
    }

    protected int readInt(DataInputStream buffer) throws IOException
    {
        return buffer.readInt();
    }

    protected void writeInt(DataOutputStream buffer, int i) throws IOException
    {
        buffer.writeInt(i);
    }

    protected FieldTable readFieldTable(DataInputStream buffer) throws AMQFrameDecodingException, IOException
    {
        return EncodingUtils.readFieldTable(buffer);
    }

    protected int getSizeOf(FieldTable table)
    {
        return EncodingUtils.encodedFieldTableLength(table);  //To change body of created methods use File | Settings | File Templates.
    }

    protected void writeFieldTable(DataOutputStream buffer, FieldTable table) throws IOException
    {
        EncodingUtils.writeFieldTableBytes(buffer, table);
    }

    protected long readLong(DataInputStream buffer) throws IOException
    {
        return buffer.readLong();
    }

    protected void writeLong(DataOutputStream buffer, long l) throws IOException
    {
        buffer.writeLong(l);
    }

    protected int getSizeOf(byte[] response)
    {
        return (response == null) ? 4 : response.length + 4;
    }

    protected void writeBytes(DataOutputStream buffer, byte[] data) throws IOException
    {
        EncodingUtils.writeBytes(buffer,data);
    }

    protected byte[] readBytes(DataInputStream buffer) throws IOException
    {
        return EncodingUtils.readBytes(buffer);
    }

    protected short readShort(DataInputStream buffer) throws IOException
    {
        return EncodingUtils.readShort(buffer);
    }

    protected void writeShort(DataOutputStream buffer, short s) throws IOException
    {
        EncodingUtils.writeShort(buffer, s);
    }

    protected Content readContent(DataInputStream buffer)
    {
        return null;
    }

    protected int getSizeOf(Content body)
    {
        return 0;
    }

    protected void writeContent(DataOutputStream buffer, Content body)
    {
    }

    protected byte readBitfield(DataInputStream buffer) throws IOException
    {
        return readByte(buffer);
    }

    protected int readUnsignedShort(DataInputStream buffer) throws IOException
    {
        return buffer.readUnsignedShort();
    }

    protected void writeBitfield(DataOutputStream buffer, byte bitfield0) throws IOException
    {
        buffer.writeByte(bitfield0);
    }

    protected void writeUnsignedShort(DataOutputStream buffer, int s) throws IOException
    {
        EncodingUtils.writeUnsignedShort(buffer, s);
    }

    protected long readUnsignedInteger(DataInputStream buffer) throws IOException
    {
        return EncodingUtils.readUnsignedInteger(buffer);
    }
    protected void writeUnsignedInteger(DataOutputStream buffer, long i) throws IOException
    {
        EncodingUtils.writeUnsignedInteger(buffer, i);
    }


    protected short readUnsignedByte(DataInputStream buffer) throws IOException
    {
        return (short) buffer.readUnsignedByte();
    }

    protected void writeUnsignedByte(DataOutputStream buffer, short unsignedByte) throws IOException
    {
        EncodingUtils.writeUnsignedByte(buffer, unsignedByte);
    }

    protected long readTimestamp(DataInputStream buffer) throws IOException
    {
        return EncodingUtils.readTimestamp(buffer);
    }

    protected void writeTimestamp(DataOutputStream buffer, long t) throws IOException
    {
        EncodingUtils.writeTimestamp(buffer, t);
    }
}
