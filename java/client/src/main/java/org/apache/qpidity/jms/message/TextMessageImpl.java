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

import javax.jms.TextMessage;
import javax.jms.JMSException;
import java.nio.ByteBuffer;
import java.io.UnsupportedEncodingException;

/**
 * Implements the interface javax.jms.TextMessage
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

    //-- constructor
    // todo

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
    public void afterMessageReceive() throws QpidException
    {
        super.afterMessageReceive();
        ByteBuffer data = getMessageData();
        if (data != null)
        {
            try
            {
                _messageText = new String(data.array(), CHARACTER_ENCODING);
            }
            catch (UnsupportedEncodingException e)
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
}

