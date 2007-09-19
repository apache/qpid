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
package org.apache.qpidity.njms.message;

import org.apache.qpidity.QpidException;

import javax.jms.*;
import java.io.IOException;
import java.io.EOFException;

/**
 * The JMS spec says:
 * StreamMessage objects support the following conversion table.
 * The marked cases must be supported. The unmarked cases must throw a JMSException.
 * The String-to-primitive conversions may throw a runtime exception if the
 * primitive's valueOf() method does not accept it as a valid String representation of the primitive.
 * <p> A value written as the row type can be read as the column type.
 * <p/>
 * |        | boolean byte short char int long float double String byte[]
 * |----------------------------------------------------------------------
 * |boolean |    X                                            X
 * |byte    |          X     X         X   X                  X
 * |short   |                X         X   X                  X
 * |char    |                     X                           X
 * |int     |                          X   X                  X
 * |long    |                              X                  X
 * |float   |                                    X     X      X
 * |double  |                                          X      X
 * |String  |    X     X     X         X   X     X     X      X
 * |byte[]  |                                                        X
 * |----------------------------------------------------------------------
 */
public class StreamMessageImpl extends BytesMessageImpl implements StreamMessage
{
    /**
     * Those statics represent incoming field types. The type of a field is
     * written first in the stream
     */
    private static final byte BOOLEAN = 1;
    private static final byte BYTE = 2;
    private static final byte CHAR = 3;
    private static final byte DOUBLE = 4;
    private static final byte FLOAT = 5;
    private static final byte INT = 6;
    private static final byte LONG = 7;
    private static final byte SHORT = 8;
    private static final byte STRING = 9;
    private static final byte BYTEARRAY = 10;
    private static final byte NULL = 11;

    /**
     * The size of the byteArray written in this stream
     */
    private int _sizeOfByteArray = 0;

    //--- Constructor
    /**
     * Constructor used by SessionImpl.
     */
    public StreamMessageImpl()
    {
        super();
        setMessageType(String.valueOf(MessageFactory.JAVAX_JMS_STREAMMESSAGE));
    }

    /**
     * Constructor used by MessageFactory
     *
     * @param message The new qpid message.
     * @throws QpidException In case of problem when receiving the message body.
     */
    protected StreamMessageImpl(org.apache.qpidity.api.Message message) throws QpidException
    {
        super(message);
    }

    //--- Interface StreamMessage
    /**
     * Reads a boolean.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |boolean |    X                                            X
     *
     * @return The boolean value read
     * @throws JMSException                  If reading a boolean fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public boolean readBoolean() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        boolean result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case BOOLEAN:
                    result = super.readBoolean();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = Boolean.valueOf(new String(bArray));
                    break;
                case NULL:
                    result = false;
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads a byte.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |byte    |          X                                       X
     *
     * @return The byte value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public byte readByte() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        byte result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case BYTE:
                    result = super.readByte();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = new Byte(new String(bArray));
                    break;
                case NULL:
                    result = Byte.valueOf(null);
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads a short.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |short   |           X    X                                X
     *
     * @return The short value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public short readShort() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        short result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case SHORT:
                    result = super.readShort();
                    break;
                case BYTE:
                    result = super.readByte();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = new Short(new String(bArray));
                    break;
                case NULL:
                    result = Short.valueOf(null);
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads a char.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |char    |                     X
     *
     * @return The char value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public char readChar() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        char result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case CHAR:
                    result = super.readChar();
                    break;
                case NULL:
                    _dataIn.reset();
                    throw new NullPointerException();
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads an Int.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |int    |         X     X         X                       X
     *
     * @return The int value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public int readInt() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        int result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case INT:
                    result = super.readInt();
                    break;
                case SHORT:
                    result = super.readShort();
                    break;
                case BYTE:
                    result = super.readByte();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = new Integer(new String(bArray));
                    break;
                case NULL:
                    result = Integer.valueOf(null);
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads an Long.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |long    |           X    X         X   X                  X
     *
     * @return The long value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public long readLong() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        long result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case LONG:
                    result = super.readLong();
                    break;
                case INT:
                    result = super.readInt();
                    break;
                case SHORT:
                    result = super.readShort();
                    break;
                case BYTE:
                    result = super.readByte();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = (new Long(new String(bArray)));
                    break;
                case NULL:
                    result = Long.valueOf(null);
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads an Float.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |float   |                                   X              X
     *
     * @return The float value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public float readFloat() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        float result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case FLOAT:
                    result = super.readFloat();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = new Float(new String(bArray));
                    break;
                case NULL:
                    result = Float.valueOf(null);
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads an double.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |double  |                                   X       X      X
     *
     * @return The double value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public double readDouble() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        double result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case DOUBLE:
                    result = super.readDouble();
                    break;
                case FLOAT:
                    result = super.readFloat();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = new Double(new String(bArray));
                    break;
                case NULL:
                    result = Double.valueOf(null);
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads an string.
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |double  |    X     X    X     X    X    X     X     X      X
     *
     * @return The string value read
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public String readString() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        String result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case BOOLEAN:
                    result = Boolean.valueOf(super.readBoolean()).toString();
                    break;
                case BYTE:
                    result = Byte.valueOf(super.readByte()).toString();
                    break;
                case SHORT:
                    result = Short.valueOf(super.readShort()).toString();
                    break;
                case CHAR:
                    result = Character.valueOf(super.readChar()).toString();
                    break;
                case INT:
                    result = Integer.valueOf(super.readInt()).toString();
                    break;
                case LONG:
                    result = Long.valueOf(super.readLong()).toString();
                    break;
                case FLOAT:
                    result = Float.valueOf(super.readFloat()).toString();
                    break;
                case DOUBLE:
                    result = Double.valueOf(super.readDouble()).toString();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    if (len == 0)
                    {
                        throw new NullPointerException("trying to read a null String");
                    }
                    byte[] bArray = new byte[len];
                    _dataIn.readFully(bArray);
                    result = new String(bArray);
                    break;
                case NULL:
                    result = null;
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads an byte[].
     * <p/>
     * |        | boolean byte short char int long float double String byte[]
     * |----------------------------------------------------------------------
     * |byte[]  |                                                        X
     * <p> The JMS spec says:
     * To read the field value, readBytes should be successively called until
     * it returns a value less than the length
     * of the read buffer. The value of the bytes in the buffer following the last byte read is undefined.
     *
     * @param value The byte array into which the data is read.
     * @return the total number of bytes read into the array, or -1 if
     *         there is no more data because the end of the byte field has been
     *         reached.
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public int readBytes(byte[] value) throws JMSException
    {
        isReadable();
        int result = -1;
        try
        {
            byte type = BYTEARRAY;
            if (_sizeOfByteArray == 0)
            {
                // we are not in the middle of reading this byte array
                _dataIn.mark(10);
                type = _dataIn.readByte();
            }
            switch (type)
            {
                case BYTEARRAY:
                    if (_sizeOfByteArray == 0)
                    {
                        // we need to read the size of this byte array
                        _sizeOfByteArray = _dataIn.readInt();
                    }
                    result = _dataIn.read(value, 0, value.length);
                    if (result != -1)
                    {
                        _sizeOfByteArray = _sizeOfByteArray - result;
                    }
                    else
                    {
                        _sizeOfByteArray = 0;
                    }
                case NULL:
                    // result = -1;
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Reads an object from the stream message.
     * <p> The JMS spec says:
     * <P>This method can be used to return, in objectified format,
     * an object in the Java programming language ("Java object") that has
     * been written to the stream with the equivalent
     * <CODE>writeObject</CODE> method call, or its equivalent primitive
     * <CODE>write<I>type</I></CODE> method.
     * <P>An attempt to call <CODE>readObject</CODE> to read a byte field
     * value into a new <CODE>byte[]</CODE> object before the full value of the
     * byte field has been read will throw a
     * <CODE>MessageFormatException</CODE>.
     *
     * @return A Java object from the stream message, in objectified
     *         format
     * @throws JMSException                  If reading fails due to some error.
     * @throws javax.jms.MessageEOFException If unexpected end of message data has been reached.
     * @throws javax.jms.MessageNotReadableException
     *                                       If the message is in write-only mode.
     * @throws MessageFormatException        If this type conversion is invalid.
     */
    public Object readObject() throws JMSException
    {
        isReadableAndNotReadingByteArray();
        Object result;
        try
        {
            _dataIn.mark(10);
            byte type = _dataIn.readByte();
            switch (type)
            {
                case BOOLEAN:
                    result = super.readBoolean();
                    break;
                case BYTE:
                    result = super.readByte();
                    break;
                case SHORT:
                    result = super.readShort();
                    break;
                case CHAR:
                    result = super.readChar();
                    break;
                case INT:
                    result = super.readInt();
                    break;
                case LONG:
                    result = super.readLong();
                    break;
                case FLOAT:
                    result = super.readFloat();
                    break;
                case DOUBLE:
                    result = super.readDouble();
                    break;
                case STRING:
                    int len = _dataIn.readInt();
                    if (len == 0)
                    {
                        result = null;
                    }
                    else
                    {
                        byte[] bArray = new byte[len];
                        _dataIn.readFully(bArray);
                        result = new String(bArray);
                    }
                    break;
                case BYTEARRAY:
                    int totalBytes = _dataIn.readInt();
                    byte[] bArray = new byte[totalBytes];
                    _dataIn.read(bArray, 0, totalBytes);
                    result = bArray;
                    break;
                case NULL:
                    result = null;
                    break;
                default:
                    _dataIn.reset();
                    throw new MessageFormatException("Invalid Object Type");
            }
        }
        catch (EOFException eof)
        {
            throw new MessageEOFException("End of file Reached when reading message");
        }
        catch (IOException io)
        {
            throw new JMSException("IO exception when reading message");
        }
        return result;
    }

    /**
     * Writes a boolean to the stream message.
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
            _dataOut.writeShort(BOOLEAN);
            super.writeBoolean(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a byte to the stream message.
     *
     * @param val The byte value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeByte(byte val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(BYTE);
            super.writeByte(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a short to the stream message.
     *
     * @param val The short value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeShort(short val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(SHORT);
            super.writeShort(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a char to the stream message.
     *
     * @param val The char value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeChar(char val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(CHAR);
            super.writeChar(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a int to the stream message.
     *
     * @param val The int value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeInt(int val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(INT);
            super.writeInt(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a long to the stream message.
     *
     * @param val The long value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeLong(long val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(LONG);
            super.writeLong(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a float to the stream message.
     *
     * @param val The float value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeFloat(float val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(FLOAT);
            super.writeFloat(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a double to the stream message.
     *
     * @param val The double value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeDouble(double val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(DOUBLE);
            super.writeDouble(val);
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a string to the stream message.
     *
     * @param val The string value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeString(String val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(STRING);
            if (val == null)
            {
                _dataOut.writeInt(0);
            }
            else
            {
                byte[] bArray = val.getBytes();
                int len = bArray.length;
                _dataOut.writeInt(len);
                _dataOut.write(bArray);
            }
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    /**
     * Writes a byte array to the stream message.
     *
     * @param val The byte array value to be written
     * @throws JMSException If writting a boolean fails due to some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void writeBytes(byte[] val) throws JMSException
    {
        isWriteable();
        try
        {
            _dataOut.writeShort(BYTEARRAY);
            _dataOut.writeInt(val.length);
            super.writeBytes(val);
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
            _dataOut.writeShort(BYTEARRAY);
            _dataOut.writeInt(length);
            super.writeBytes(val, offset, length);
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
        isWriteable();
        try
        {
            if (val == null)
            {
                _dataOut.writeShort(NULL);
            }
            else if (val instanceof Byte)
            {
                writeByte((Byte) val);
            }
            else if (val instanceof Boolean)
            {
                writeBoolean((Boolean) val);
            }
            else if (val instanceof Short)
            {
                writeShort((Short) val);
            }
            else if (val instanceof Integer)
            {
                writeInt((Integer) val);
            }
            else if (val instanceof Long)
            {
                writeLong((Long) val);
            }
            else if (val instanceof Double)
            {
                writeDouble((Double) val);
            }
            else if (val instanceof Float)
            {
                writeFloat((Float) val);
            }
            else if (val instanceof Character)
            {
                writeChar((Character) val);
            }
            else if (val instanceof String)
            {
                writeString((String) val);
            }
            else if (val instanceof byte[])
            {
                writeBytes((byte[]) val);
            }
            else
            {
                throw new MessageFormatException(
                        "The data type of the object specified as the value to writeObject " + "was of an invalid type.");
            }
        }
        catch (IOException e)
        {
            throw new JMSException("IO problem when writting " + e.getLocalizedMessage());
        }
    }

    //-- overwritten methods
    /**
     * Test whether this message is readable by throwing a MessageNotReadableException if this
     * message cannot be read.
     *
     * @throws javax.jms.MessageNotReadableException
     *          If this message cannot be read.
     * @throws javax.jms.MessageFormatException
     *          If reading a byte array.
     */
    protected void isReadableAndNotReadingByteArray() throws MessageNotReadableException, MessageFormatException
    {
        if (_dataIn == null)
        {
            throw new MessageNotReadableException("Cannot read this message");
        }
        if (_sizeOfByteArray > 0)
        {
            throw new MessageFormatException(
                    "Read of object attempted while incomplete byteArray stored in message " + "- finish reading byte array first.");
        }
    }
}
