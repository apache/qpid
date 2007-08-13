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

/**
 * A factory for JMS messages
 */
public class MessageFactory
{
    /**
     * JMS Message hierarchy.
     */
    public static final byte JAVAX_JMS_MESSAGE = 1;
    public static final byte JAVAX_JMS_TEXTMESSAGE = 2;
    public static final byte JAVAX_JMS_STREAMMESSAGE = 3;
    public static final byte JAVAX_JMS_BYTESMESSAGE = 4;
    public static final byte JAVAX_JMS_OBJECTMESSAGE = 5;
    public static final byte JAVAX_JMS_MAPMESSAGE = 6;

    /**
     * Create a QpidMessage subclass according to the JMS message type.
     *
     * @param message The received qpidity messsage
     * @return The newly craeted JMS message
     * @throws QpidException If an appropriate Message class cannot be created.
     */
    public static QpidMessage getQpidMessage(org.apache.qpidity.api.Message message) throws QpidException
    {
        QpidMessage result = null;
        byte type = Byte.valueOf(message.getMessageProperties().getType());
        switch (type)
        {
            case JAVAX_JMS_MESSAGE:
                result = new MessageImpl(message);
                break;
            case JAVAX_JMS_TEXTMESSAGE:
                result = new TextMessageImpl(message);
                break;
            case JAVAX_JMS_STREAMMESSAGE:
                result = new StreamMessageImpl(message);
                break;
            case JAVAX_JMS_BYTESMESSAGE:
                result = new BytesMessageImpl(message);
                break;
            case JAVAX_JMS_OBJECTMESSAGE:
                result = new ObjectMessageImpl(message);
                break;
            case JAVAX_JMS_MAPMESSAGE:
                result = new MapMessageImpl(message);
                break;
            default:
                throw new QpidException(
                        "Message type identifier is not mapped " + "to a Message class in the current factory: " + type,
                        null, null);
        }
        return result;
    }
}
