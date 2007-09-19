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

import javax.jms.TextMessage;
import javax.jms.JMSException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.io.UnsupportedEncodingException;

/**
 * Implements the interface javax.njms.TextMessage
 */
public class TextMessageImpl extends MessageImpl implements TextMessage
{
    /**
     * The character encoding for converting non ASCII characters
     * Default UTF-16
     */
    private static final String CHARACTER_ENCODING = "UTF-16";

    /**
     * This message text. The byte form is set when this message is sent
     * the text is set when the message is received.
     */
    private String _messageText;

    //--- Constructor
    /**
     * Constructor used by SessionImpl.
     */
    public TextMessageImpl()
    {
        super();
        setMessageType(String.valueOf(MessageFactory.JAVAX_JMS_STREAMMESSAGE));
    }

    /**
     * Constructor used by MessageFactory
     *
     * @param message The new qpid message.
     * @throws QpidException In case of IO problem when reading the received message.
     */
    protected TextMessageImpl(org.apache.qpidity.api.Message message) throws QpidException
    {
        super(message);
    }

    //--- interface TextMessage

    public String getText() throws JMSException
    {
        return _messageText;
    }

    /**
     * Set the text (String) of this TextMessage.
     *
     * @param text The String containing the text.
     * @throws JMSException If setting the text fails due some error.
     * @throws javax.jms.MessageNotWriteableException
     *                      If message in read-only mode.
     */
    public void setText(String text) throws JMSException
    {
        isWriteable();
        _messageText = text;
    }

    //-- Overwritten methods

    /**
     * This method is invoked before this message is dispatched.
     * <p>This class uses it to convert its text payload into a ByteBuffer
     */
    public void beforeMessageDispatch() throws QpidException
    {
        if (_messageText != null)
        {
            // set this message data
            try
            {
                setMessageData(ByteBuffer.wrap(_messageText.getBytes(CHARACTER_ENCODING)));
            }
            catch (UnsupportedEncodingException e)
            {
                throw new QpidException("Problem when encoding text " + _messageText, null, e);
            }
        }
        super.beforeMessageDispatch();
    }


    /**
     * This method is invoked after this message has been received.
     */
    @Override
    public void afterMessageReceive() throws QpidException
    {
        super.afterMessageReceive();
        ByteBuffer messageData = getMessageData();
        if (messageData != null)
        {
            try
            {
                _messageText = getString();
            }
            catch (Exception e)
            {
                throw new QpidException("Problem when decoding text", null, e);
            }
        }
    }

    /**
     * Clear out the message body. Clearing a message's body does not clear
     * its header values or property entries.
     * <P>If this message body was read-only, calling this method leaves
     * the message body is in the same state as an empty body in a newly
     * created message.
     *
     * @throws JMSException If clearing this message body fails to due to some error.
     */
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _messageText = null;
    }

    /**
     * This method is taken from Mina code
     * 
     * Reads a <code>NUL</code>-terminated string from this buffer using the
     * specified <code>decoder</code> and returns it.  This method reads
     * until the limit of this buffer if no <tt>NUL</tt> is found.
     *
     * @return
     * @throws java.nio.charset.CharacterCodingException
     *
     */
    public String getString() throws CharacterCodingException
    {
        if (!getMessageData().hasRemaining())
        {
            return "";
        }
        Charset charset = Charset.forName(CHARACTER_ENCODING);
        CharsetDecoder decoder = charset.newDecoder();

        boolean utf16 = decoder.charset().name().startsWith("UTF-16");

        int oldPos = getMessageData().position();
        int oldLimit = getMessageData().limit();
        int end = -1;
        int newPos;

        if (!utf16)
        {
            end = indexOf((byte) 0x00);
            if (end < 0)
            {
                newPos = end = oldLimit;
            }
            else
            {
                newPos = end + 1;
            }
        }
        else
        {
            int i = oldPos;
            for (; ;)
            {
                boolean wasZero = getMessageData().get(i) == 0;
                i++;

                if (i >= oldLimit)
                {
                    break;
                }

                if (getMessageData().get(i) != 0)
                {
                    i++;
                    if (i >= oldLimit)
                    {
                        break;
                    }
                    else
                    {
                        continue;
                    }
                }

                if (wasZero)
                {
                    end = i - 1;
                    break;
                }
            }

            if (end < 0)
            {
                newPos = end = oldPos + ((oldLimit - oldPos) & 0xFFFFFFFE);
            }
            else
            {
                if (end + 2 <= oldLimit)
                {
                    newPos = end + 2;
                }
                else
                {
                    newPos = end;
                }
            }
        }

        if (oldPos == end)
        {
            getMessageData().position(newPos);
            return "";
        }

        getMessageData().limit(end);
        decoder.reset();

        int expectedLength = (int) (getMessageData().remaining() * decoder.averageCharsPerByte()) + 1;
        CharBuffer out = CharBuffer.allocate(expectedLength);
        for (; ;)
        {
            CoderResult cr;
            if (getMessageData().hasRemaining())
            {
                cr = decoder.decode(getMessageData(), out, true);
            }
            else
            {
                cr = decoder.flush(out);
            }

            if (cr.isUnderflow())
            {
                break;
            }

            if (cr.isOverflow())
            {
                CharBuffer o = CharBuffer.allocate(out.capacity() + expectedLength);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }

            if (cr.isError())
            {
                // Revert the buffer back to the previous state.
                getMessageData().limit(oldLimit);
                getMessageData().position(oldPos);
                cr.throwException();
            }
        }

        getMessageData().limit(oldLimit);
        getMessageData().position(newPos);
        return out.flip().toString();
    }

    /**
     * Returns the first occurence position of the specified byte from the current position to
     * the current limit.
     *
     * @return <tt>-1</tt> if the specified byte is not found
     * @param b
     */
    public int indexOf(byte b)
    {
        if (getMessageData().hasArray())
        {
            int arrayOffset = getMessageData().arrayOffset();
            int beginPos = arrayOffset + getMessageData().position();
            int limit = arrayOffset + getMessageData().limit();
            byte[] array = getMessageData().array();

            for (int i = beginPos; i < limit; i++)
            {
                if (array[i] == b)
                {
                    return i - arrayOffset;
                }
            }
        }
        else
        {
            int beginPos = getMessageData().position();
            int limit = getMessageData().limit();

            for (int i = beginPos; i < limit; i++)
            {
                if (getMessageData().get(i) == b)
                {
                    return i;
                }
            }
        }
        return -1;
    }
}

