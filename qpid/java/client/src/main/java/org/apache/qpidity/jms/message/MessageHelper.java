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

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 *
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
            result = ((Character) obj).charValue();
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
    public static boolean isPrimitive(Object value) throws JMSException
    {
        // Innocent till proven guilty
        boolean isPrimitive = true;
        if (!((value instanceof String) || (value instanceof Boolean) || (value instanceof Character) || ((value instanceof Number) && !((value instanceof BigDecimal) || (value instanceof BigInteger)))))
        {
            isPrimitive = false;
        }
        return isPrimitive;
    }
}
