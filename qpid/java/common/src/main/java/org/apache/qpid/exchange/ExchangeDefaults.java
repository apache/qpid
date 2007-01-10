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
package org.apache.qpid.exchange;

import org.apache.qpid.framing.AMQShortString;

public class ExchangeDefaults
{
    public final static AMQShortString TOPIC_EXCHANGE_NAME = new AMQShortString("amq.topic");

    public final static AMQShortString TOPIC_EXCHANGE_CLASS = new AMQShortString("topic");

    public final static AMQShortString DIRECT_EXCHANGE_NAME = new AMQShortString("amq.direct");

    public final static AMQShortString DIRECT_EXCHANGE_CLASS = new AMQShortString("direct");

    public final static AMQShortString HEADERS_EXCHANGE_NAME = new AMQShortString("amq.match");

    public final static AMQShortString HEADERS_EXCHANGE_CLASS = new AMQShortString("headers");

    public final static AMQShortString FANOUT_EXCHANGE_NAME = new AMQShortString("amq.fanout");

    public final static AMQShortString FANOUT_EXCHANGE_CLASS = new AMQShortString("fanout");
}
