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

package org.apache.qpid.ra;

import java.util.Enumeration;

import javax.jms.JMSException;
import javax.jms.MapMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for a message
 *
 */
public class QpidRAMapMessage extends QpidRAMessage implements MapMessage
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAMapMessage.class);

   /**
    * Create a new wrapper
    *
    * @param message the message
    * @param session the session
    */
   public QpidRAMapMessage(final MapMessage message, final QpidRASessionImpl session)
   {
      super(message, session);

      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + message + ", " + session + ")");
      }
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public boolean getBoolean(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getBoolean(" + name + ")");
      }

      return ((MapMessage)_message).getBoolean(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public byte getByte(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getByte(" + name + ")");
      }

      return ((MapMessage)_message).getByte(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public byte[] getBytes(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getBytes(" + name + ")");
      }

      return ((MapMessage)_message).getBytes(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public char getChar(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getChar(" + name + ")");
      }

      return ((MapMessage)_message).getChar(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public double getDouble(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getDouble(" + name + ")");
      }

      return ((MapMessage)_message).getDouble(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public float getFloat(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getFloat(" + name + ")");
      }

      return ((MapMessage)_message).getFloat(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public int getInt(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getInt(" + name + ")");
      }

      return ((MapMessage)_message).getInt(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public long getLong(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getLong(" + name + ")");
      }

      return ((MapMessage)_message).getLong(name);
   }

   /**
    * Get the map names
    * @return The values
    * @exception JMSException Thrown if an error occurs
    */
   public Enumeration<?> getMapNames() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMapNames()");
      }

      return ((MapMessage)_message).getMapNames();
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public Object getObject(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getObject(" + name + ")");
      }

      return ((MapMessage)_message).getObject(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public short getShort(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getShort(" + name + ")");
      }

      return ((MapMessage)_message).getShort(name);
   }

   /**
    * Get
    * @param name The name
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public String getString(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getString(" + name + ")");
      }

      return ((MapMessage)_message).getString(name);
   }

   /**
    * Does the item exist
    * @param name The name
    * @return True / false
    * @exception JMSException Thrown if an error occurs
    */
   public boolean itemExists(final String name) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("itemExists(" + name + ")");
      }

      return ((MapMessage)_message).itemExists(name);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setBoolean(final String name, final boolean value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setBoolean(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setBoolean(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setByte(final String name, final byte value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setByte(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setByte(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @param offset The offset
    * @param length The length
    * @exception JMSException Thrown if an error occurs
    */
   public void setBytes(final String name, final byte[] value, final int offset, final int length) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setBytes(" + name + ", " + value + ", " + offset + ", " + length + ")");
      }

      ((MapMessage)_message).setBytes(name, value, offset, length);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setBytes(final String name, final byte[] value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setBytes(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setBytes(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setChar(final String name, final char value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setChar(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setChar(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setDouble(final String name, final double value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDouble(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setDouble(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setFloat(final String name, final float value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setFloat(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setFloat(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setInt(final String name, final int value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setInt(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setInt(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setLong(final String name, final long value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setLong(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setLong(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setObject(final String name, final Object value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setObject(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setObject(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setShort(final String name, final short value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setShort(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setShort(name, value);
   }

   /**
    * Set
    * @param name The name
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void setString(final String name, final String value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setString(" + name + ", " + value + ")");
      }

      ((MapMessage)_message).setString(name, value);
   }
}
