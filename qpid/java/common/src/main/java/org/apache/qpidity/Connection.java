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
package org.apache.qpidity;

import java.util.HashMap;
import java.util.Map;

/**
 * Connection
 *
 * @author Rafael H. Schloming
 *
 * @todo the channels map should probably be replaced with something
 * more efficient, e.g. an array or a map implementation that can use
 * short instead of Short
 */

class Connection implements Handler<Frame>
{

    final private Map<Short,Channel> channels = new HashMap<Short,Channel>();
    final private StructFactory factory = new StructFactory_v0_10();

    public void handle(Frame frame)
    {
        Channel channel = channels.get(frame.getChannel());
        if (channel == null)
        {
            channel = new Channel(this);
            channels.put(frame.getChannel(), channel);
        }

        channel.handle(frame);
    }

    public StructFactory getFactory()
    {
        return factory;
    }

}
