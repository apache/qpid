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
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class MessageConverterRegistry
{
    private static Map<Class<? extends ServerMessage>, Map<Class<? extends ServerMessage>, MessageConverter>> _converters =
            new HashMap<Class<? extends ServerMessage>, Map<Class<? extends ServerMessage>, MessageConverter>>();

    static
    {

        for(MessageConverter<? extends ServerMessage, ? extends ServerMessage> converter : (new QpidServiceLoader()).instancesOf(MessageConverter.class))
        {
            Map<Class<? extends ServerMessage>, MessageConverter> map = _converters.get(converter.getInputClass());
            if(map == null)
            {
                map = new HashMap<Class<? extends ServerMessage>, MessageConverter>();
                _converters.put(converter.getInputClass(), map);
            }
            map.put(converter.getOutputClass(),converter);
        }
    }

    public static <M  extends ServerMessage,N  extends ServerMessage> MessageConverter<M, N> getConverter(Class<M> from, Class<N> to)
    {
        Map<Class<? extends ServerMessage>, MessageConverter> map = _converters.get(from);
        if(map == null)
        {
            map = _converters.get(ServerMessage.class);
        }
        return map == null ? null : map.get(to);
    }
}
