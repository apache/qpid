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

package org.apache.qpid.nclient.message;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQPInvalidClassException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.util.Enumeration;

public class MessageHeaders
{
    private static final Logger _logger = Logger.getLogger(MessageHeaders.class);
    
    private AMQShortString _contentType;

    private AMQShortString _encoding;
    
    private AMQShortString _destination;
    
    private AMQShortString _exchange;

    private FieldTable _jmsHeaders;

    private short _deliveryMode;

    private short _priority;

    private AMQShortString _correlationId;

    private AMQShortString _replyTo;

    private long _expiration;

    private AMQShortString _messageId;

    private long _timestamp;

    private AMQShortString _type;

    private AMQShortString _userId;

    private AMQShortString _appId;

    private AMQShortString _transactionId;

    private AMQShortString _routingKey;
    
    private int _size;
    
    public int getSize()
    {
		return _size;
	}

	public void setSize(int size)
    {
		this._size = size;
	}

	public MessageHeaders()
    {
    }

    public AMQShortString getContentType()
    {
        return _contentType;
    }

    public void setContentType(AMQShortString contentType)
    {
        _contentType = contentType;
    }

    public AMQShortString getEncoding()
    {
        return _encoding;
    }

    public void setEncoding(AMQShortString encoding)
    {
        _encoding = encoding;
    }

    public FieldTable getJMSHeaders()
    {
        if (_jmsHeaders == null)
        {
            setJMSHeaders(FieldTableFactory.newFieldTable());
        }

        return _jmsHeaders;
    }

    public void setJMSHeaders(FieldTable headers)
    {
        _jmsHeaders = headers;
    }


    public short getDeliveryMode()
    {
        return _deliveryMode;
    }

    public void setDeliveryMode(short deliveryMode)
    {
        _deliveryMode = deliveryMode;
    }

    public short getPriority()
    {
        return _priority;
    }

    public void setPriority(short priority)
    {
        _priority = priority;
    }

    public AMQShortString getCorrelationId()
    {
        return _correlationId;
    }

    public void setCorrelationId(AMQShortString correlationId)
    {
        _correlationId = correlationId;
    }

    public AMQShortString getReplyTo()
    {
        return _replyTo;
    }
    
    public void setReplyTo(AMQShortString replyTo)
    {
        _replyTo = replyTo;
    }

    public long getExpiration()
    {
        return _expiration;
    }

    public void setExpiration(long expiration)
    {
        _expiration = expiration;
    }


    public AMQShortString getMessageId()
    {
        return _messageId;
    }

    public void setMessageId(AMQShortString messageId)
    {
        _messageId = messageId;
    }

    public long getTimestamp()
    {
        return _timestamp;
    }

    public void setTimestamp(long timestamp)
    {
        _timestamp = timestamp;
    }

    public AMQShortString getType()
    {
        return _type;
    }

    public void setType(AMQShortString type)
    {
        _type = type;
    }

    public AMQShortString getUserId()
    {
        return _userId;
    }

    public void setUserId(AMQShortString userId)
    {
        _userId = userId;
    }

    public AMQShortString getAppId()
    {
        return _appId;
    }

    public void setAppId(AMQShortString appId)
    {
        _appId = appId;
    }

    // MapMessage  Interface

    public boolean getBoolean(AMQShortString string) throws JMSException
    {
        Boolean b = getJMSHeaders().getBoolean(string);

        if (b == null)
        {
            if (getJMSHeaders().containsKey(string))
            {
                Object str = getJMSHeaders().getObject(string);

                if (str == null || !(str instanceof AMQShortString))
                {
                    throw new MessageFormatException("getBoolean can't use " + string + " item.");
                }
                else
                {
                    return Boolean.valueOf(((AMQShortString)str).asString());
                }
            }
            else
            {
                b = Boolean.valueOf(null);
            }
        }

        return b;
    }

    public char getCharacter(AMQShortString string) throws JMSException
    {
        Character c = getJMSHeaders().getCharacter(string);

        if (c == null)
        {
            if (getJMSHeaders().isNullStringValue(string.asString()))
            {
                throw new NullPointerException("Cannot convert null char");
            }
            else
            {
                throw new MessageFormatException("getChar can't use " + string + " item.");
            }
        }
        else
        {
            return (char) c;
        }
    }

    public byte[] getBytes(AMQShortString string) throws JMSException
    {
        byte[] bs = getJMSHeaders().getBytes(string);

        if (bs == null)
        {
            throw new MessageFormatException("getBytes can't use " + string + " item.");
        }
        else
        {
            return bs;
        }
    }

    public byte getByte(AMQShortString string) throws JMSException
    {
            Byte b = getJMSHeaders().getByte(string);
            if (b == null)
            {
                if (getJMSHeaders().containsKey(string))
                {
                    Object str = getJMSHeaders().getObject(string);

                    if (str == null || !(str instanceof AMQShortString))
                    {
                        throw new MessageFormatException("getByte can't use " + string + " item.");
                    }
                    else
                    {
                        return Byte.valueOf(((AMQShortString)str).asString());
                    }
                }
                else
                {
                    b = Byte.valueOf(null);
                }
            }

            return b;
    }

    public short getShort(AMQShortString string) throws JMSException
    {
            Short s = getJMSHeaders().getShort(string);

            if (s == null)
            {
                s = Short.valueOf(getByte(string));
            }

            return s;
    }

    public int getInteger(AMQShortString string) throws JMSException
    {
            Integer i = getJMSHeaders().getInteger(string);

            if (i == null)
            {
                i = Integer.valueOf(getShort(string));
            }

            return i;
    }

    public long getLong(AMQShortString string) throws JMSException
    {
            Long l = getJMSHeaders().getLong(string);

            if (l == null)
            {
                l = Long.valueOf(getInteger(string));
            }

            return l;
    }

    public float getFloat(AMQShortString string) throws JMSException
    {
            Float f = getJMSHeaders().getFloat(string);

            if (f == null)
            {
                if (getJMSHeaders().containsKey(string))
                {
                    Object str = getJMSHeaders().getObject(string);

                    if (str == null || !(str instanceof AMQShortString))
                    {
                        throw new MessageFormatException("getFloat can't use " + string + " item.");
                    }
                    else
                    {
                        return Float.valueOf(((AMQShortString)str).asString());
                    }
                }
                else
                {
                    f = Float.valueOf(null);
                }

            }

            return f;
    }

    public double getDouble(AMQShortString string) throws JMSException
    {
            Double d = getJMSHeaders().getDouble(string);

            if (d == null)
            {
                d = Double.valueOf(getFloat(string));
            }

            return d;
    }

    public AMQShortString getString(AMQShortString string) throws JMSException
    {
        AMQShortString s = new AMQShortString(getJMSHeaders().getString(string.asString()));

        if (s == null)
        {
            if (getJMSHeaders().containsKey(string))
            {
                Object o = getJMSHeaders().getObject(string);
                if (o instanceof byte[])
                {
                    throw new MessageFormatException("getObject couldn't find " + string + " item.");
                }
                else
                {
                    if (o == null)
                    {
                        return null;
                    }
                    else
                    {
                        s = (AMQShortString) o;
                    }
                }
            }
        }

        return s;
    }

    public Object getObject(AMQShortString string) throws JMSException
    {
        return getJMSHeaders().getObject(string);
    }

    public void setBoolean(AMQShortString string, boolean b) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setBoolean(string, b);
    }

    public void setChar(AMQShortString string, char c) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setChar(string, c);
    }

    public Object setBytes(AMQShortString string, byte[] bytes)
    {
        return getJMSHeaders().setBytes(string, bytes);
    }

    public Object setBytes(AMQShortString string, byte[] bytes, int start, int length)
    {
        return getJMSHeaders().setBytes(string, bytes, start, length);
    }

    public void setByte(AMQShortString string, byte b) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setByte(string, b);
    }

    public void setShort(AMQShortString string, short i) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setShort(string, i);
    }

    public void setInteger(AMQShortString string, int i) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setInteger(string, i);
    }

    public void setLong(AMQShortString string, long l) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setLong(string, l);
    }

    public void setFloat(AMQShortString string, float v) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setFloat(string, v);
    }

    public void setDouble(AMQShortString string, double v) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setDouble(string, v);
    }

    public void setString(AMQShortString string, AMQShortString string1) throws JMSException
    {
        checkPropertyName(string);
        getJMSHeaders().setString(string.asString(), string1.asString());
    }

    public void setObject(AMQShortString string, Object object) throws JMSException
    {
        checkPropertyName(string);
        try
        {
            getJMSHeaders().setObject(string, object);
        }
        catch (AMQPInvalidClassException aice)
        {
            throw new MessageFormatException("Only primatives are allowed object is:" + object.getClass());
        }
    }

    public boolean itemExists(AMQShortString string) throws JMSException
    {
        return getJMSHeaders().containsKey(string);
    }

    public Enumeration getPropertyNames()
    {
        return getJMSHeaders().getPropertyNames();
    }

    public void clear()
    {
        getJMSHeaders().clear();
    }

    public boolean propertyExists(AMQShortString propertyName)
    {
        return getJMSHeaders().propertyExists(propertyName);
    }

    public Object put(Object key, Object value)
    {
        return getJMSHeaders().setObject(key.toString(), value);
    }

    public Object remove(AMQShortString propertyName)
    {
        return getJMSHeaders().remove(propertyName);
    }

    public boolean isEmpty()
    {
        return getJMSHeaders().isEmpty();
    }

    public void writeToBuffer(ByteBuffer data)
    {
        getJMSHeaders().writeToBuffer(data);
    }

    public Enumeration getMapNames()
    {
        return getPropertyNames();
    }

    protected static void checkPropertyName(CharSequence propertyName)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if (propertyName.length() == 0)
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        checkIdentiferFormat(propertyName);
    }

    protected static void checkIdentiferFormat(CharSequence propertyName)
    {
//        JMS requirements 3.5.1 Property Names
//        Identifiers:
//        - An identifier is an unlimited-length character sequence that must begin
//          with a Java identifier start character; all following characters must be Java
//          identifier part characters. An identifier start character is any character for
//          which the method Character.isJavaIdentifierStart returns true. This includes
//          '_' and '$'. An identifier part character is any character for which the
//          method Character.isJavaIdentifierPart returns true.
//        - Identifiers cannot be the names NULL, TRUE, or FALSE.
//        Â– Identifiers cannot be NOT, AND, OR, BETWEEN, LIKE, IN, IS, or
//          ESCAPE.
//        Â– Identifiers are either header field references or property references. The
//          type of a property value in a message selector corresponds to the type
//          used to set the property. If a property that does not exist in a message is
//          referenced, its value is NULL. The semantics of evaluating NULL values
//          in a selector are described in Section 3.8.1.2, Â“Null Values.Â”
//        Â– The conversions that apply to the get methods for properties do not
//          apply when a property is used in a message selector expression. For
//          example, suppose you set a property as a string value, as in the
//          following:
//              myMessage.setStringProperty("NumberOfOrders", "2");
//          The following expression in a message selector would evaluate to false,
//          because a string cannot be used in an arithmetic expression:
//          "NumberOfOrders > 1"
//        Â– Identifiers are case sensitive.
//        Â– Message header field references are restricted to JMSDeliveryMode,
//          JMSPriority, JMSMessageID, JMSTimestamp, JMSCorrelationID, and
//          JMSType. JMSMessageID, JMSCorrelationID, and JMSType values may be
//          null and if so are treated as a NULL value.

        if (Boolean.getBoolean("strict-jms"))
        {
            // JMS start character
            if (!(Character.isJavaIdentifierStart(propertyName.charAt(0))))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' does not start with a valid JMS identifier start character");
            }

            // JMS part character
            int length = propertyName.length();
            for (int c = 1; c < length; c++)
            {
                if (!(Character.isJavaIdentifierPart(propertyName.charAt(c))))
                {
                    throw new IllegalArgumentException("Identifier '" + propertyName + "' contains an invalid JMS identifier character");
                }
            }




            // JMS invalid names
            if ((propertyName.equals("NULL")
                 || propertyName.equals("TRUE")
                 || propertyName.equals("FALSE")
                 || propertyName.equals("NOT")
                 || propertyName.equals("AND")
                 || propertyName.equals("OR")
                 || propertyName.equals("BETWEEN")
                 || propertyName.equals("LIKE")
                 || propertyName.equals("IN")
                 || propertyName.equals("IS")
                 || propertyName.equals("ESCAPE")))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' is not allowed in JMS");
            }
        }

    }

	public AMQShortString getTransactionId()
    {
		return _transactionId;
	}

	public void setTransactionId(AMQShortString id)
    {
		_transactionId = id;
	}

	public AMQShortString getDestination()
    {
		return _destination;
	}

	public void setDestination(AMQShortString destination)
    {
		this._destination = destination;
	}

	public AMQShortString getExchange()
    {
		return _exchange;
	}

	public void setExchange(AMQShortString exchange)
    {
		this._exchange = exchange;
	}

	public AMQShortString getRoutingKey()
    {
		return _routingKey;
	}

	public void setRoutingKey(AMQShortString routingKey)
    {
		this._routingKey = routingKey;
	}
}

   
