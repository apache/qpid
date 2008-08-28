package org.apache.qpid.nclient.util;
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


import java.nio.ByteBuffer;

import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.api.Message;

public abstract class ReadOnlyMessage implements Message
{
    MessageProperties _messageProperties;
    DeliveryProperties _deliveryProperties;
        
    public void appendData(byte[] src)
    {
        throw new UnsupportedOperationException("This Message is read only after the initial source");
    }

    public void appendData(ByteBuffer src)
    {
        throw new UnsupportedOperationException("This Message is read only after the initial source");
    }

    public DeliveryProperties getDeliveryProperties()
    {
        return _deliveryProperties;
    }

    public MessageProperties getMessageProperties()
    {
        return _messageProperties;
    }
    
    public void clearData()
    {
        throw new UnsupportedOperationException("This Message is read only after the initial source, cannot clear data");
    }
}
