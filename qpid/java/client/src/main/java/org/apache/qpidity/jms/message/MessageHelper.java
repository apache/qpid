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

import javax.jms.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Enumeration;

/**
 * This is an helper class for performing data convertion
 */
public class MessageHelper
{
    /**
     * Convert an object into a boolean value
     *
     * @param obj object that may contain boolean value
     * @return A boolean value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static boolean convertToBoolean(Object obj) throws JMSException
    {
        boolean result;
        if (obj instanceof Boolean)
        {
            result = (Boolean) obj;
        }
        else if (obj instanceof String)
        {
            result = ((String) obj).equalsIgnoreCase("true");
        }
        else
        {
            throw new MessageFormatException("boolean property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a byte value
     *
     * @param obj The object that may contain byte value
     * @return The convertToed byte value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static byte convertToByte(Object obj) throws JMSException
    {
        byte result;
        if (obj instanceof Byte)
        {
            result = ((Number) obj).byteValue();
        }
        else if (obj instanceof String)
        {
            result = Byte.parseByte((String) obj);
        }
        else
        {
            throw new MessageFormatException("byte property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a short value
     *
     * @param obj The object that may contain short value
     * @return The convertToed short value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static short convertToShort(Object obj) throws JMSException
    {
        short result;
        if ((obj instanceof Short) || (obj instanceof Byte))
        {
            result = ((Number) obj).shortValue();
        }
        else if (obj instanceof String)
        {
            result = Short.parseShort((String) obj);
        }
        else
        {
            throw new MessageFormatException("short property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a int value
     *
     * @param obj The object that may contain int value
     * @return The convertToed int value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static int convertToInt(Object obj) throws JMSException
    {
        int result;
        if ((obj instanceof Integer) || (obj instanceof Byte) || (obj instanceof Short))
        {
            result = ((Number) obj).intValue();
        }
        else if (obj instanceof String)
        {
            result = Integer.parseInt((String) obj);
        }
        else
        {
            throw new MessageFormatException("int property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a long value
     *
     * @param obj The object that may contain long value
     * @return The convertToed long value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static long convertToLong(Object obj) throws JMSException
    {
        long result;
        if ((obj instanceof Number) && !((obj instanceof Float) || (obj instanceof Double)))
        {
            result = ((Number) obj).longValue();
        }
        else if (obj instanceof String)
        {

            result = Long.parseLong((String) obj);
        }
        else
        {
            throw new MessageFormatException("long property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a float value
     *
     * @param obj The object that may contain float value
     * @return The convertToed float value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static float convertToFloat(Object obj) throws JMSException
    {
        float result;
        if (obj instanceof Float)
        {
            result = ((Number) obj).floatValue();
        }
        else if (obj instanceof String)
        {
            result = Float.parseFloat((String) obj);
        }
        else
        {
            throw new MessageFormatException("float property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a double value
     *
     * @param obj The object that may contain double value
     * @return The convertToed double value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static double convertToDouble(Object obj) throws JMSException
    {
        double result;
        if ((obj instanceof Double) || (obj instanceof Float))
        {
            result = ((Number) obj).doubleValue();
        }
        else if (obj instanceof String)
        {
            result = Double.parseDouble((String) obj);
        }
        else
        {
            throw new MessageFormatException("double property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a char value
     *
     * @param obj The object that may contain char value
     * @return The convertToed char value.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public static char convertToChar(Object obj) throws JMSException
    {
        char result;
        if (obj instanceof Character)
        {
            result = (Character) obj;
        }
        else
        {
            throw new MessageFormatException("char property type convertion error",
                                             "Messasge property type convertion error");
        }
        return result;
    }

    /**
     * Convert an object into a String value
     *
     * @param obj The object that may contain String value
     * @return The convertToed String value.
     */
    public static String convertToString(Object obj)
    {
        String stringValue;
        if (obj instanceof String)
        {
            stringValue = (String) obj;
        }
        else
        {
            stringValue = obj.toString();
        }
        return stringValue;
    }

    /**
     * Check if the passed object represents Java primitive type
     *
     * @param value object for inspection
     * @return true if object represent Java primitive type; false otherwise
     */
    public static boolean isPrimitive(Object value)
    {
        // Innocent till proven guilty
        boolean isPrimitive = true;
        if (!((value instanceof String) || (value instanceof Boolean) || (value instanceof Character) || ((value instanceof Number) && !((value instanceof BigDecimal) || (value instanceof BigInteger)))))
        {
            isPrimitive = false;
        }
        return isPrimitive;
    }

    /**
     * Transform a foreign message into an equivalent QPID representation.
     *
     * @param message The foreign message to be converted.
     * @return A native message.
     * @throws JMSException In case of problem when converting the message.
     */
    public static MessageImpl transformMessage(Message message) throws JMSException
    {
        MessageImpl messageImpl;

        if (message instanceof BytesMessage)
        {
            messageImpl = transformBytesMessage((BytesMessage) message);
        }
        else if (message instanceof MapMessage)
        {
            messageImpl = transformMapMessage((MapMessage) message);
        }
        else if (message instanceof ObjectMessage)
        {
            messageImpl = transformObjectMessage((ObjectMessage) message);
        }
        else if (message instanceof StreamMessage)
        {
            messageImpl = transformStreamMessage((StreamMessage) message);
        }
        else if (message instanceof TextMessage)
        {
            messageImpl = transformTextMessage((TextMessage) message);
        }
        else
        {
            messageImpl = new MessageImpl();
        }
        transformHeaderAndProperties(message, messageImpl);
        return messageImpl;
    }

    //---- Private methods     
    /**
     * Exposed JMS defined properties on converted message:
     * JMSDestination   - we don't set here
     * JMSDeliveryMode  - we don't set here
     * JMSExpiration    - we don't set here
     * JMSPriority      - we don't set here
     * JMSMessageID     - we don't set here
     * JMSTimestamp     - we don't set here
     * JMSCorrelationID - set
     * JMSReplyTo       - set
     * JMSType          - set
     * JMSRedlivered    - we don't set here
     *
     * @param message   The foreign message to be converted.
     * @param nativeMsg A native Qpid message.
     * @throws JMSException In case of problem when converting the message.
     */
    private static void transformHeaderAndProperties(Message message, MessageImpl nativeMsg) throws JMSException
    {
        //Set the correlation ID
        String correlationID = message.getJMSCorrelationID();
        if (correlationID != null)
        {
            nativeMsg.setJMSCorrelationID(correlationID);
        }
        //Set JMS ReplyTo
        if (message.getJMSReplyTo() != null)
        {
            nativeMsg.setJMSReplyTo(message.getJMSReplyTo());
        }
        //Set JMS type
        String jmsType = message.getJMSType();
        if (jmsType != null)
        {
            nativeMsg.setJMSType(jmsType);
        }
        // Sets all non-JMS defined properties on converted message
        Enumeration propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements())
        {
            String propertyName = String.valueOf(propertyNames.nextElement());
            if (!propertyName.startsWith("JMSX_"))
            {
                Object value = message.getObjectProperty(propertyName);
                nativeMsg.setObjectProperty(propertyName, value);
            }
        }
    }

    /**
     * Transform a BytesMessage.
     *
     * @param bytesMessage a BytesMessage to be converted.
     * @return a native BytesMessage.
     * @throws JMSException In case of problem when converting the message.
     */
    private static BytesMessageImpl transformBytesMessage(BytesMessage bytesMessage) throws JMSException
    {
        //reset the BytesMessage (makes the body read-only and repositions
        // the stream of bytes to the beginning
        bytesMessage.reset();
        BytesMessageImpl nativeMsg = new BytesMessageImpl();
        byte[] buf = new byte[1024];
        int len;
        while ((len = bytesMessage.readBytes(buf)) != -1)
        {
            nativeMsg.writeBytes(buf, 0, len);
        }
        return nativeMsg;
    }

    /**
     * Transform a MapMessage.
     *
     * @param mapMessage a MapMessage to be converted.
     * @return a native MapMessage.
     * @throws JMSException In case of problem when converting the message.
     */
    private static MapMessageImpl transformMapMessage(MapMessage mapMessage) throws JMSException
    {
        MapMessageImpl nativeMsg = new MapMessageImpl();
        Enumeration mapNames = mapMessage.getMapNames();
        while (mapNames.hasMoreElements())
        {
            String name = (String) mapNames.nextElement();
            nativeMsg.setObject(name, mapMessage.getObject(name));
        }
        return nativeMsg;
    }

    /**
     * Transform an ObjectMessage.
     *
     * @param objectMessage a ObjectMessage to be converted.
     * @return a native ObjectMessage.
     * @throws JMSException In case of problem when converting the message.
     */
    private static ObjectMessageImpl transformObjectMessage(ObjectMessage objectMessage) throws JMSException
    {
        ObjectMessageImpl nativeMsg = new ObjectMessageImpl();
        nativeMsg.setObject(objectMessage.getObject());
        return nativeMsg;
    }

    /**
     * Transform a StreamMessage.
     *
     * @param streamMessage a StreamMessage to be converted.
     * @return a native StreamMessage.
     * @throws JMSException In case of problem when converting the message.
     */
    private static StreamMessageImpl transformStreamMessage(StreamMessage streamMessage) throws JMSException
    {
        StreamMessageImpl nativeMsg = new StreamMessageImpl();
        try
        {
            //reset the stream message
            streamMessage.reset();
            while (true)
            {
                nativeMsg.writeObject(streamMessage.readObject());
            }
        }
        catch (MessageEOFException e)
        {
            // we're at the end so don't mind the exception
        }
        return nativeMsg;
    }

    /**
     * Transform a TextMessage.
     *
     * @param textMessage a TextMessage to be converted.
     * @return a native TextMessage.
     * @throws JMSException In case of problem when converting the message.
     */
    private static TextMessageImpl transformTextMessage(TextMessage textMessage) throws JMSException
    {
        TextMessageImpl nativeMsg = new TextMessageImpl();
        nativeMsg.setText(textMessage.getText());
        return nativeMsg;
    }

}
