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
package org.apache.qpid.management.domain.handler.impl;

import org.apache.qpid.management.domain.handler.base.BaseMessageHandler;
import org.apache.qpid.transport.codec.ManagementDecoder;
import org.apache.qpid.transport.util.Logger;

public class MethodResponseMessageHandler extends BaseMessageHandler
{
    private final static Logger LOGGER = Logger.get(MethodResponseMessageHandler.class);
    
    public void process (ManagementDecoder decoder, int sequenceNumber)
    {
        LOGGER.debug("<QMAN-200009> : Incoming method response message.");
        
        long statusCode = decoder.readUint32();
        String statusText = decoder.readStr8();
        
        LOGGER.debug("<QMAN-200010> : Status code : %s", statusCode);
        LOGGER.debug("<QMAN-200011> : Status text : %s", statusText);
    }
}