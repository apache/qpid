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
package org.apache.qpid.server.protocol;

import java.util.HashMap;
import java.util.Map;

public class LinkRegistry
{
    private final Map<String, LinkModel> _sendingLinks = new HashMap<String, LinkModel>();
    private final Map<String, LinkModel> _receivingLinks = new HashMap<String, LinkModel>();

    public synchronized LinkModel getDurableSendingLink(String name)
    {
        return _sendingLinks.get(name);
    }

    public synchronized boolean registerSendingLink(String name, LinkModel link)
    {
        if(_sendingLinks.containsKey(name))
        {
            return false;
        }
        else
        {
            _sendingLinks.put(name, link);
            return true;
        }
    }

    public synchronized boolean unregisterSendingLink(String name)
    {
        if(!_sendingLinks.containsKey(name))
        {
            return false;
        }
        else
        {
            _sendingLinks.remove(name);
            return true;
        }
    }

    public synchronized LinkModel getDurableReceivingLink(String name)
    {
        return _receivingLinks.get(name);
    }

    public synchronized  boolean registerReceivingLink(String name, LinkModel link)
    {
        if(_receivingLinks.containsKey(name))
        {
            return false;
        }
        else
        {
            _receivingLinks.put(name, link);
            return true;
        }
    }
}
