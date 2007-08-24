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
package org.apache.qpidity.jms.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpidity.QpidException;

import javax.jms.*;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Implements javax.jms.BytesMessage
 */
public class BytesMessageImpl extends MessageImpl implements BytesMessage
{
    /**
     * this BytesMessageImpl's logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(BytesMessageImpl.class);

    /**
     * An input stream for reading this message data
     * This stream wrappes the received byteBuffer.
     */
    protected DataInputStream _dataIn = null;

    /**
     * Used to store written data.
     */
    protected ByteArrayOutputStream _storedData = new ByteArrayOutputStream();

    /**
     * DataOutputStream used to write the data
     */
    protected DataOutputStream _dataOut = new DataOutputStream(_storedData);

    //--- Constructor
    /**
     * Constructor used by SessionImpl.
     */
    public BytesMessageImpl()
    {
        super();
        setMessageType(String.valueOf(MessageFactory.JAVAX_JMS_BYTESMESSAGE));
    }

    /**
     * Constructor used by MessageFactory
     *
     * @param message The new qpid message.
     * @throws QpidException In case of problem when receiving the message body.
     */
    protected BytesMessageImpl(org.apache.qpidity.api.Message message) throws QpidException
    {
        super(message);
        try
        {
            ByteBuffer b = message.readData();
            byte[] a = new byte[b.limit()];
            b.get(a);
            _dataIn = new DataInputStream(new ByteArrayInputStream(a));
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    //--- BytesMessage API
    /**
     * Gets the number of bytes of the message body when the message
     * is in read-only mode.
     * <p> The value returned is the entire length of the message
     * body, regardless of where the pointer for reading the message
     * is currently located.
     *
     * @return Number of bytes in the message
     * @throws JMSException If reading the message body length fails due to some error.
     * @throws javax.jms.MessageNotReadableException
     *                      If the message is in write-only mode.
     */
    public long getBodyLength() throws JMSException
    {
        isReadable();
        return getMessageData().capacity();
    }

    /**
     * Reads a boolean.
     *
     * @return The boolean value read
     * @throws JMSException        If reading fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public boolean readBoolean() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readBoolean();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads a signed 8-bit.
     *
     * @return The signed 8-bit read
     * @throws JMSException        If reading a signed 8-bit fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public byte readByte() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readByte();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads an unsigned 8-bit.
     *
     * @return The signed 8-bit read
     * @throws JMSException        If reading an unsigned 8-bit fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public int readUnsignedByte() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readUnsignedByte();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads a short.
     *
     * @return The short read
     * @throws JMSException        If reading a short fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public short readShort() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readShort();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads an unsigned short.
     *
     * @return The unsigned short read
     * @throws JMSException        If reading an unsigned short fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public int readUnsignedShort() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readUnsignedShort();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads a char.
     *
     * @return The char read
     * @throws JMSException        If reading a char fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public char readChar() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readChar();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads an int.
     *
     * @return The int read
     * @throws JMSException        If reading an int fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public int readInt() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readInt();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads a long.
     *
     * @return The long read
     * @throws JMSException        If reading a long fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public long readLong() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readLong();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Read a float.
     *
     * @return The float read
     * @throws JMSException        If reading a float fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public float readFloat() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readFloat();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Read a double.
     *
     * @return The double read
     * @throws JMSException        If reading a double fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public double readDouble() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readDouble();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads a string that has been encoded using a modified UTF-8 format.
     *
     * @return The String read
     * @throws JMSException        If reading a String fails due to some error.
     * @throws MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                             If the message is in write-only mode.
     */
    public String readUTF() throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.readUTF();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException("Reach end of data when reading message data");
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads a byte array from the bytes message data.
     * <p> JMS sepc says:
     * <P>If the length of array <code>bytes</code> is less than the number of
     * bytes remaining to be read from the stream, the array should
     * be filled. A subsequent call reads the next increment, and so on.
     * <P>If the number of bytes remaining in the stream is less than the
     * length of
     * array <code>bytes</code>, the bytes should be read into the array.
     * The return value of the total number of bytes read will be less than
     * the length of the array, indicating that there are no more bytes left
     * to be read from the stream. The next read of the stream returns -1.
     *
     * @param b The array into which the data is read.
     * @return The total number of bytes read into the buffer, or -1 if
     *         there is no more data because the end of the stream has been reached
     * @throws JMSException If reading a byte array fails due to some error.
     * @throws javax.jms.MessageNotReadableException
     *                      If the message is in write-only mode.
     */
    public int readBytes(byte[] b) throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.read(b);
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Reads a portion of the bytes message data.
     * <p> The JMS spec says
     * <P>If the length of array <code>b</code> is less than the number of
     * bytes remaining to be read from the stream, the array should
     * be filled. A subsequent call reads the next increment, and so on.
     * <P>If the number of bytes remaining in the stream is less than the
     * length of array <code>b</code>, the bytes should be read into the array.
     * The return value of the total number of bytes read will be less than
     * the length of the array, indicating that there are no more bytes left
     * to be read from the stream. The next read of the stream returns -1.
     * <p> If <code>length</code> is negative, or
     * <code>length</code> is greater than the length of the array
     * <code>b</code>, then an <code>IndexOutOfBoundsException</code> is
     * thrown. No bytes will be read from the stream for this exception case.
     *
     * @param b      The buffer into which the data is read
     * @param length The number of bytes to read; must be less than or equal to length.
     * @return The total number of bytes read into the buffer, or -1 if
     *         there is no more data because the end of the data has been reached
     * @throws JMSException If reading a byte array fails due to some error.
     * @throws javax.jms.MessageNotReadableException
     *                      If the message is in write-only mode.
     */
    public int readBytes(byte[] b, int length) throws JMSException
    {
        isReadable();
        try
        {
            return _dataIn.read(b, 0, length);
        }
        catch (IOException ioe)
        {
            throw new JMSException("Problem when reading data", ioe.getLocalizedMessage());
        }
    }

    /**
     * Writes a boolean to the bytes message.
     *
     * @param val The boolean value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeBoolean(boolean val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeBoolean(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a byte to the bytes message.
     *
     * @param val The byte value to be written
     * @throws JMSException If writting a byte fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeByte(byte val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeByte(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a short to the bytes message.
     *
     * @param val The short value to be written
     * @throws JMSException If writting a short fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeShort(short val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }

    }

    /**
     * Writes a char to the bytes message.
     *
     * @param c The char value to be written
     * @throws JMSException If writting a char fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeChar(char c) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeChar(c);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes an int to the bytes message.
     *
     * @param val The int value to be written
     * @throws JMSException If writting an int fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeInt(int val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeInt(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }

    }

    /**
     * Writes a long to the bytes message.
     *
     * @param val The long value to be written
     * @throws JMSException If writting a long fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeLong(long val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeLong(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a float to the bytes message.
     *
     * @param val The float value to be written
     * @throws JMSException If writting a float fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeFloat(float val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeFloat(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a double to the bytes message.
     *
     * @param val The double value to be written
     * @throws JMSException If writting a double fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeDouble(double val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeDouble(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a string to the bytes message.
     *
     * @param val The string value to be written
     * @throws JMSException If writting a string fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeUTF(String val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeUTF(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }

    }

    /**
     * Writes a byte array to the bytes message.
     *
     * @param bytes The byte array value to be written
     * @throws JMSException If writting a byte array fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeBytes(byte[] bytes) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.write(bytes);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a portion of byte array to the bytes message.
     *
     * @param val The byte array value to be written
     * @throws JMSException If writting a byte array fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeBytes(byte[] val, int offset, int length) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.write(val, offset, length);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes an Object to the bytes message.
     * JMS spec says:
     * <p>This method works only for the objectified primitive
     * object types Integer, Double, Long, String and byte
     * arrays.
     *
     * @param val The short value to be written
     * @throws JMSException           If writting a short fails due to some error.
     * @throws NullPointerException   if the parameter val is null.
     * @throws MessageFormatException If the object is of an invalid type.
     * @throws javax.jms.MessageNotWriteableException
     *                                If the message is in read-only mode.
     */
    public void writeObject(Object val) throws JMSException
    {
        if (val == null)
        {
            throw new NullPointerException("Cannot write null value to message");
        }
        if (val instanceof byte[])
        {
            writeBytes((byte[]) val);
        }
        else if (val instanceof String)
        {
            writeUTF((String) val);
        }
        else if (val instanceof Boolean)
        {
            writeBoolean(((Boolean) val).booleanValue());
        }
        else if (val instanceof Number)
        {
            if (val instanceof Byte)
            {
                writeByte(((Byte) val).byteValue());
            }
            else if (val instanceof Short)
            {
                writeShort(((Short) val).shortValue());
            }
            else if (val instanceof Integer)
            {
                writeInt(((Integer) val).intValue());
            }
            else if (val instanceof Long)
            {
                writeLong(((Long) val).longValue());
            }
            else if (val instanceof Float)
            {
                writeFloat(((Float) val).floatValue());
            }
            else if (val instanceof Double)
            {
                writeDouble(((Double) val).doubleValue());
            }
            else
            {
                throw new MessageFormatException("Trying to write an invalid obejct type: " + val);
            }
        }
        else if (val instanceof Character)
        {
            writeChar(((Character) val).charValue());
        }
        else
        {
            throw new MessageFormatException("Trying to write an invalid obejct type: " + val);
        }
    }

    /**
     * Puts the message body in read-only mode and repositions the stream of
     * bytes to the beginning.
     *
     * @throws JMSException           If resetting the message fails due to some internal error.
     * @throws MessageFormatException If the message has an invalid format.
     */
    public void reset() throws JMSException
    {
        _readOnly = true;
        if (_dataIn == null)
        {
            // We were writting on this messsage so now read it
            _dataIn = new DataInputStream(new ByteArrayInputStream(_storedData.toByteArray()));
        }
        else
        {
            // We were reading so reset it
            try
            {
                _dataIn.reset();
            }
            catch (IOException e)
            {
                if (_logger.isDebugEnabled())
                {
                    // we log this exception as this should not happen
                    _logger.debug("Problem when resetting message: ", e);
                }
                throw new JMSException("Problem when resetting message: " + e.getLocalizedMessage());
            }
        }
    }

    //-- overwritten methods
    /**
     * Clear out the message body. Clearing a message's body does not clear
     * its header values or property entries.
     * <p>If this message body was read-only, calling this method leaves
     * the message body is in the same state as an empty body in a newly
     * created message.
     *
     * @throws JMSException If clearing this message body fails to due to some error.
     */
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _dataIn = null;
        _storedData = new ByteArrayOutputStream();
        _dataOut = new DataOutputStream(_storedData);
    }


    /**
     * This method is invoked before a message dispatch operation.
     *
     * @throws org.apache.qpidity.QpidException
     *          If the destination is not set
     */
    public void beforeMessageDispatch() throws QpidException
    {
        if (_dataOut.size() > 0)
        {
            setMessageData(ByteBuffer.wrap(_storedData.toByteArray()));
        }
        super.beforeMessageDispatch();
    }

    /**
     * This method is invoked after this message is received.
     *
     * @throws QpidException
     */
    public void afterMessageReceive() throws QpidException
    {
        super.afterMessageReceive();
        ByteBuffer messageData = getMessageData();
        if (messageData != null)
        {
            _dataIn = new DataInputStream(new ByteArrayInputStream(messageData.array()));
        }
    }

    //-- helper mehtods
    /**
     * Test whether this message is readable by throwing a MessageNotReadableException if this
     * message cannot be read.
     *
     * @throws MessageNotReadableException If this message cannot be read.
     */
    protected void isReadable() throws MessageNotReadableException
    {
        if (_dataIn == null)
        {
            throw new MessageNotReadableException("Cannot read this message");
        }
    }
}

