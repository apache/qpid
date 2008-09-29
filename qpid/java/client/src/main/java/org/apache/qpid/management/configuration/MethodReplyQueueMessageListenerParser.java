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
 * Parser used for building mappings between method-reply queue message listeners and an opcode.
 * 
 * <handler>
        <opcode>i</opcode>
        <class-name>org.apache.qpid.management.domain.handler.impl.InstrumentationMessageHandler</class-name>
    </handler>
 *     
 * @author Andrea Gazzarini
 */
class MethodReplyQueueMessageListenerParser implements IParser
{
    private MessageHandlerMapping  _mapping = new MessageHandlerMapping();
    private String _currentValue;
    
    /**
     * Callback : the given value is the text content of the current node.
     */
    public void setCurrrentAttributeValue (String value)
    {
        this._currentValue = value;
    }

    /**
     * Callback: each time the end of an element is reached this method is called.
     * It's here that the built mapping is injected into the configuration.
     */
    public void setCurrentAttributeName (String name)
    {
        switch (Tag.get(name))
        {
            case OPCODE: 
            {
                _mapping.setOpcode(_currentValue);
                break;
            }
            case CLASS_NAME: 
            {
                _mapping.setMessageHandlerClass(_currentValue);
                break;
            }
            case HANDLER: 
            {
                Configuration.getInstance().addMethodReplyMessageHandlerMapping(_mapping);
                _mapping = new MessageHandlerMapping();
                break;
            }
        }
    }
}
