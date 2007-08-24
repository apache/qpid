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

import org.apache.qpidity.QpidException;

import javax.jms.MapMessage;
import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Implements javax.jms.MapMessage
 */
public class MapMessageImpl extends MessageImpl implements MapMessage
{

    /**
     * The MapMessage's payload.
     */
    private Map<String, Object> _map = new HashMap<String, Object>();

    //--- Constructor
    /**
     * Constructor used by SessionImpl.
     */
    public MapMessageImpl()
    {
        super();
        setMessageType(String.valueOf(MessageFactory.JAVAX_JMS_MAPMESSAGE));
    }

    /**
     * Constructor used by MessageFactory
     *
     * @param message The new qpid message.
     * @throws QpidException In case of IO problem when reading the received message.
     */
    protected MapMessageImpl(org.apache.qpidity.api.Message message) throws QpidException
    {
        super(message);
    }

    //-- Map Message API
    /**
     * Indicates whether an key exists in this MapMessage.
     *
     * @param key the name of the key to test
     * @return true if the key exists
     * @throws JMSException If determinein if the key exists fails due to some internal error
     */
    public boolean itemExists(String key) throws JMSException
    {
        return _map.containsKey(key);
    }

    /**
     * Returns the booleanvalue with the specified key.
     *
     * @param key The key name.
     * @return The boolean value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public boolean getBoolean(String key) throws JMSException
    {
        boolean result = false;
        if (_map.containsKey(key))
        {
            try
            {
                Object objValue = _map.get(key);
                if (objValue != null)
                {
                    result = MessageHelper.convertToBoolean(_map.get(key));
                }
            }
            catch (ClassCastException e)
            {
                throw new MessageFormatException("Wrong type for key: " + key);
            }
        }
        return result;
    }

    /**
     * Returns the byte value with the specified name.
     *
     * @param key The key name.
     * @return The byte value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public byte getByte(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            throw new NumberFormatException("Wrong type for key: " + key);
        }
        return MessageHelper.convertToByte(objValue);
    }

    /**
     * Returns the <CODE>short</CODE> value with the specified name.
     *
     * @param key The key name.
     * @return The <CODE>short</CODE> value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public short getShort(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            throw new NumberFormatException("Wrong type for key: " + key);
        }
        return MessageHelper.convertToShort(objValue);
    }

    /**
     * Returns the Unicode character value with the specified name.
     *
     * @param key The key name.
     * @return The Unicode charactervalue with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public char getChar(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            throw new java.lang.NullPointerException();
        }
        return MessageHelper.convertToChar(objValue);
    }

    /**
     * Returns the intvalue with the specified name.
     *
     * @param key The key name.
     * @return The int value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public int getInt(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            throw new NumberFormatException("Wrong type for key: " + key);
        }
        return MessageHelper.convertToInt(objValue);
    }

    /**
     * Returns the longvalue with the specified name.
     *
     * @param key The key name.
     * @return The long value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public long getLong(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            throw new NumberFormatException("Wrong type for key: " + key);
        }
        return MessageHelper.convertToLong(objValue);
    }

    /**
     * Returns the float value with the specified name.
     *
     * @param key The key name.
     * @return The float value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public float getFloat(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            throw new NumberFormatException("Wrong type for key: " + key);
        }
        return MessageHelper.convertToFloat(objValue);
    }

    /**
     * Returns the double value with the specified name.
     *
     * @param key The key name.
     * @return The double value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public double getDouble(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            throw new NumberFormatException("Wrong type for key: " + key);
        }
        return MessageHelper.convertToDouble(objValue);
    }

    /**
     * Returns the String value with the specified name.
     *
     * @param key The key name.
     * @return The String value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public String getString(String key) throws JMSException
    {
        String result = null;
        Object objValue = _map.get(key);
        if (objValue != null)
        {
            if (objValue instanceof byte[])
            {
                throw new NumberFormatException("Wrong type for key: " + key);
            }
            else
            {
                result = objValue.toString();
            }
        }
        return result;
    }

    /**
     * Returns the byte array value with the specified name.
     *
     * @param key The key name.
     * @return The byte value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If this type conversion is invalid.
     */
    public byte[] getBytes(String key) throws JMSException
    {
        Object objValue = _map.get(key);
        if (objValue == null)
        {
            return null;
        }
        if (objValue instanceof byte[])
        {
            byte[] value = (byte[]) objValue;
            byte[] toReturn = new byte[value.length];
            System.arraycopy(value, 0, toReturn, 0, value.length);
            return toReturn;
        }
        throw new MessageFormatException("Wrong type for key: " + key);
    }

    /**
     * Returns the value of the object with the specified name.
     *
     * @param key The key name.
     * @return The byte value with the specified key.
     * @throws JMSException If reading the message fails due to some internal error.
     */
    public Object getObject(String key) throws JMSException
    {
        try
        {
            Object objValue = _map.get(key);
            if (objValue == null)
            {
                return null;
            }
            else if (objValue instanceof byte[])
            {
                byte[] value = (byte[]) objValue;
                byte[] toReturn = new byte[value.length];
                System.arraycopy(value, 0, toReturn, 0, value.length);
                return toReturn;
            }
            else
            {
                return objValue;
            }
        }
        catch (java.lang.ClassCastException cce)
        {
            throw new MessageFormatException("Wrong type for key: " + key);
        }
    }

    /**
     * Returns an Enumeration of all the keys
     *
     * @return an enumeration of all the keys in this MapMessage
     * @throws JMSException If reading the message fails due to some internal error.
     */
    public Enumeration getMapNames() throws JMSException
    {
        Vector<String> propVector = new Vector<String>(_map.keySet());
        return propVector.elements();
    }

    /**
     * Sets a boolean value with the specified key into the Map.
     *
     * @param key   The key name.
     * @param value The boolean value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setBoolean(String key, boolean value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a byte value with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The byte value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setByte(String key, byte value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a shortvalue with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The short value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setShort(String key, short value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a Unicode character value with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The character value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setChar(String key, char value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets an intvalue with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The int value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setInt(String key, int value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a long value with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The long value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setLong(String key, long value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a float value with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The float value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setFloat(String key, float value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a double value with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The double value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setDouble(String key, double value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a String value with the specified name into the Map.
     *
     * @param key   The key name.
     * @param value The String value to set in the Map.
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setString(String key, String value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        _map.put(key, value);
    }

    /**
     * Sets a byte array value with the specified name into the Map.
     *
     * @param key   the name of the byte array
     * @param value the byte array value to set in the Map; the array
     *              is copied so that the value for <CODE>name</CODE> will
     *              not be altered by future modifications
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setBytes(String key, byte[] value) throws JMSException, NullPointerException
    {
        isWriteable();
        checkNotNullKey(key);
        byte[] newBytes = new byte[value.length];
        System.arraycopy(value, 0, newBytes, 0, value.length);
        _map.put(key, value);
    }

    /**
     * Sets a portion of the byte array value with the specified name into the
     * Map.
     *
     * @param key   the name of the byte array
     * @param value the byte array value to set in the Map; the array
     *              is copied so that the value for <CODE>name</CODE> will
     *              not be altered by future modifications
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setBytes(String key, byte[] value, int offset, int length) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        byte[] newBytes = new byte[length];
        System.arraycopy(value, offset, newBytes, 0, length);
        _map.put(key, newBytes);
    }

    /**
     * Sets an object value with the specified name into the Map.
     *
     * @param key   the name of the byte array
     * @param value the byte array value to set in the Map; the array
     *              is copied so that the value for <CODE>name</CODE> will
     *              not be altered by future modifications
     * @throws JMSException             If  writting the message fails due to some internal error.
     * @throws IllegalArgumentException If the key is nul or an empty string.
     * @throws javax.jms.MessageNotWriteableException
     *                                  If the message is in read-only mode.
     */
    public void setObject(String key, Object value) throws JMSException, IllegalArgumentException
    {
        isWriteable();
        checkNotNullKey(key);
        if ((value instanceof Boolean) || (value instanceof Byte) || (value instanceof Short) || (value instanceof Integer) || (value instanceof Long) || (value instanceof Character) || (value instanceof Float) || (value instanceof Double) || (value instanceof String) || (value instanceof byte[]) || (value == null))
        {
            _map.put(key, value);
        }
        else
        {
            throw new MessageFormatException("Cannot set property " + key + " to value " + value + "of type " + value
                    .getClass().getName() + ".");
        }
    }

    //-- Overwritten methods
    /**
     * This method is invoked before this message is dispatched.
     */
    @Override
    public void beforeMessageDispatch() throws QpidException
    {
        try
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(_map);
            byte[] bytes = baos.toByteArray();
            setMessageData(ByteBuffer.wrap(bytes));
        }
        catch (java.io.IOException ioe)
        {
            throw new QpidException("problem when dispatching message", null, ioe);
        }
        super.beforeMessageDispatch();
    }


    /**
     * This method is invoked after this message has been received.
     */
    public void afterMessageReceive() throws QpidException
    {
        super.afterMessageReceive();
        ByteBuffer messageData = getMessageData();
        if (messageData != null)
        {
            try
            {
                ByteArrayInputStream bais = new ByteArrayInputStream(messageData.array());
                ObjectInputStream ois = new ObjectInputStream(bais);
                _map = (Map<String, Object>) ois.readObject();
            }
            catch (IOException ioe)
            {
                throw new QpidException(
                        "Unexpected error during rebuild of message in afterReceive(): " + "- IO Exception", null, ioe);
            }
            catch (ClassNotFoundException e)
            {
                throw new QpidException(
                        "Unexpected error during rebuild of message in afterReceive(): " + "- Could not find the required class in classpath.",
                        null, e);
            }
        }
    }

    //-- protected methods
    /**
     * This method throws an <CODE>IllegalArgumentException</CODE> if the supplied parameter is null.
     *
     * @param key The key to check.
     * @throws IllegalArgumentException If the key is null.
     */
    private void checkNotNullKey(String key) throws IllegalArgumentException
    {
        if (key == null || key.equals(""))
        {
            throw new IllegalArgumentException("Key cannot be null");
        }
    }
}
