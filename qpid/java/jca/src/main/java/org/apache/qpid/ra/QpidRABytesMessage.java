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

import javax.jms.BytesMessage;
import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for a message
 *
 */
public class QpidRABytesMessage extends QpidRAMessage implements BytesMessage
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRABytesMessage.class);

   /**
    * Create a new wrapper
    * @param message the message
    * @param session the session
    */
   public QpidRABytesMessage(final BytesMessage message, final QpidRASessionImpl session)
   {
      super(message, session);

      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + Util.asString(message) + ", " + session + ")");
      }
   }

   /**
    * Get body length
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public long getBodyLength() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getBodyLength()");
      }

      return ((BytesMessage)_message).getBodyLength();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public boolean readBoolean() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readBoolean()");
      }

      return ((BytesMessage)_message).readBoolean();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public byte readByte() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readByte()");
      }

      return ((BytesMessage)_message).readByte();
   }

   /**
    * Read
    * @param value The value
    * @param length The length
    * @return The result
    * @exception JMSException Thrown if an error occurs
    */
   public int readBytes(final byte[] value, final int length) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readBytes(" + value + ", " + length + ")");
      }

      return ((BytesMessage)_message).readBytes(value, length);
   }

   /**
    * Read
    * @param value The value
    * @return The result
    * @exception JMSException Thrown if an error occurs
    */
   public int readBytes(final byte[] value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readBytes(" + value + ")");
      }

      return ((BytesMessage)_message).readBytes(value);
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public char readChar() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readChar()");
      }

      return ((BytesMessage)_message).readChar();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public double readDouble() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readDouble()");
      }

      return ((BytesMessage)_message).readDouble();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public float readFloat() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readFloat()");
      }

      return ((BytesMessage)_message).readFloat();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public int readInt() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readInt()");
      }

      return ((BytesMessage)_message).readInt();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public long readLong() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readLong()");
      }

      return ((BytesMessage)_message).readLong();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public short readShort() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readShort()");
      }

      return ((BytesMessage)_message).readShort();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public int readUnsignedByte() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readUnsignedByte()");
      }

      return ((BytesMessage)_message).readUnsignedByte();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public int readUnsignedShort() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readUnsignedShort()");
      }

      return ((BytesMessage)_message).readUnsignedShort();
   }

   /**
    * Read
    * @return The value
    * @exception JMSException Thrown if an error occurs
    */
   public String readUTF() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("readUTF()");
      }

      return ((BytesMessage)_message).readUTF();
   }

   /**
    * Reset
    * @exception JMSException Thrown if an error occurs
    */
   public void reset() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("reset()");
      }

      ((BytesMessage)_message).reset();
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeBoolean(final boolean value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeBoolean(" + value + ")");
      }

      ((BytesMessage)_message).writeBoolean(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeByte(final byte value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeByte(" + value + ")");
      }

      ((BytesMessage)_message).writeByte(value);
   }

   /**
    * Write
    * @param value The value
    * @param offset The offset
    * @param length The length
    * @exception JMSException Thrown if an error occurs
    */
   public void writeBytes(final byte[] value, final int offset, final int length) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeBytes(" + value + ", " + offset + ", " + length + ")");
      }

      ((BytesMessage)_message).writeBytes(value, offset, length);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeBytes(final byte[] value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeBytes(" + value + ")");
      }

      ((BytesMessage)_message).writeBytes(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeChar(final char value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeChar(" + value + ")");
      }

      ((BytesMessage)_message).writeChar(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeDouble(final double value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeDouble(" + value + ")");
      }

      ((BytesMessage)_message).writeDouble(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeFloat(final float value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeFloat(" + value + ")");
      }

      ((BytesMessage)_message).writeFloat(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeInt(final int value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeInt(" + value + ")");
      }

      ((BytesMessage)_message).writeInt(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeLong(final long value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeLong(" + value + ")");
      }

      ((BytesMessage)_message).writeLong(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeObject(final Object value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeObject(" + Util.asString(value) + ")");
      }

      ((BytesMessage)_message).writeObject(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeShort(final short value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeShort(" + value + ")");
      }

      ((BytesMessage)_message).writeShort(value);
   }

   /**
    * Write
    * @param value The value
    * @exception JMSException Thrown if an error occurs
    */
   public void writeUTF(final String value) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("writeUTF(" + value + ")");
      }

      ((BytesMessage)_message).writeUTF(value);
   }
}
