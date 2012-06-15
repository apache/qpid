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
package org.apache.qpid.messaging;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 *  The message factory is responsible for returning
 *  a concrete implementation of the message interface
 *  with the given content.
 *
 *  A reference to the Message Factory can be obtained via
 *  the Connection object.
 *  @see Connection#getMessageFactory()
 */
public interface MessageFactory
{
    /**
     * Supported Message Types.
     * Use
     */
    public enum MessageType {BINARY, STRING, MAP, LIST}

    public Message createMessage(String text) throws MessageEncodingException;

    public Message createMessage(byte[] bytes) throws MessageEncodingException;

    public Message createMessage(ByteBuffer buf) throws MessageEncodingException;

    public Message createMessage(Map<String,Object> map) throws MessageEncodingException;

    public Message createMessage(List<Object> list) throws MessageEncodingException;

    public String getContentAsString(Message m) throws MessageEncodingException;

    public Map<String,Object> getContentAsMap(Message m) throws MessageEncodingException;

    public List<Object> getContentAsList(Message m) throws MessageEncodingException;

    /**
     * You could use this method to map your custom content-type to one
     * of the supported MessageType's (@see MessageType), provided the content
     * of the message conforms to the expected type.
     *
     * Ex.  foo/bar -> STRING, will tell the client to treat any message that has
     * the content-type foo/bar to be treated as a STRING Message.
     *
     * Currently supported content types are as follows.
     * default                  - BINARY
     * application/octet-stream - BINARY
     * text/plain               - STRING
     * text/xml                 - STRING
     * amqp/map                 - MAP
     * amqp-0-10/map            - MAP
     * amqp/list                - LIST
     * amqp-0-10/list           - LIST
     *
     * @param contentType The content type you want to register.
     * @param type The MessageType @see MessageType
     */
    public void registerContentType(String contentType, MessageType type);
}
