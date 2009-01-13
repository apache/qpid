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
package org.apache.qpid.management.configuration;

/**
 * Message Handler mapping used for associating an opcode with a message handler.
 * 
 * @author Andrea Gazzarini
 */
class MessageHandlerMapping
{
    private Character _opcode;
    private String _handlerClass;
    
    /**
     * Builds an empty message handler mapping.
     */
    MessageHandlerMapping()
    {
    }
    
    /**
     * Builds a new mapping with the given opcode and handler class.
     * 
     * @param opcode the opcode.
     * @param handlerClass the handler class.
     */
    MessageHandlerMapping(Character opcode, String handlerClass) {
        this._opcode = opcode;
        this._handlerClass = handlerClass;
    }
    
    /**
     * Returns the opcode of this mapping.
     * 
     * @return the code of this mapping.
     */
    Character getOpcode ()
    {
        return _opcode;
    }

    /**
     * Sets the opcode for this mapping.
     * 
     * @param codeAsString the opcode as a string.
     */
    void setOpcode (String codeAsString)
    {
      this._opcode = codeAsString.charAt(0);
    }

    /**
     * Returns the message handler for this mapping.
     * 
     * @return the message handler for this mapping.
     */
    String getMessageHandlerClass()
    {
        return _handlerClass;
    }

    /**
     * Sets the message handler of this mapping. 
     * 
     * @param handlerClass the handler class as a string.
     */
    void setMessageHandlerClass(String handlerClass)
    {
        this._handlerClass = handlerClass;
    }
}