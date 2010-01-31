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
package org.apache.qpid.server.exchange.topic;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.AMQShortStringTokenizer;
import org.apache.qpid.server.exchange.TopicExchange;

import java.util.List;
import java.util.ArrayList;

public class TopicNormalizer
{
    private static final byte TOPIC_SEPARATOR = (byte)'.';
    private static final byte HASH_BYTE = (byte)'#';
    private static final byte STAR_BYTE = (byte)'*';

    private static final AMQShortString TOPIC_SEPARATOR_AS_SHORTSTRING = new AMQShortString(".");
    private static final AMQShortString AMQP_STAR_TOKEN = new AMQShortString("*");
    private static final AMQShortString AMQP_HASH_TOKEN = new AMQShortString("#");

    public static AMQShortString normalize(AMQShortString routingKey)
    {
        if(routingKey == null)
        {
            return AMQShortString.EMPTY_STRING;
        }
        else if(!(routingKey.contains(HASH_BYTE) || routingKey.contains(STAR_BYTE)))
        {
            return routingKey;
        }
        else
        {
            AMQShortStringTokenizer routingTokens = routingKey.tokenize(TOPIC_SEPARATOR);

            List<AMQShortString> subscriptionList = new ArrayList<AMQShortString>();

            while (routingTokens.hasMoreTokens())
            {
                subscriptionList.add(routingTokens.nextToken());
            }

            int size = subscriptionList.size();

            for (int index = 0; index < size; index++)
            {
                // if there are more levels
                if ((index + 1) < size)
                {
                    if (subscriptionList.get(index).equals(AMQP_HASH_TOKEN))
                    {
                        if (subscriptionList.get(index + 1).equals(AMQP_HASH_TOKEN))
                        {
                            // we don't need #.# delete this one
                            subscriptionList.remove(index);
                            size--;
                            // redo this normalisation
                            index--;
                        }

                        if (subscriptionList.get(index + 1).equals(AMQP_STAR_TOKEN))
                        {
                            // we don't want #.* swap to *.#
                            // remove it and put it in at index + 1
                            subscriptionList.add(index + 1, subscriptionList.remove(index));
                        }
                    }
                } // if we have more levels
            }



            AMQShortString normalizedString = AMQShortString.join(subscriptionList, TOPIC_SEPARATOR_AS_SHORTSTRING);

            return normalizedString;
        }
    }
}
