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
package org.apache.qpid.test.unit.close;

import org.apache.qpid.client.AMQAuthenticationException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeclareOkBody;
import org.apache.qpid.test.utils.QpidTestCase;

import javax.jms.Session;

/** QPID-1085 */
public class JavaServerCloseRaceConditionTest extends QpidTestCase
{
    public void test() throws Exception
    {

        AMQConnection connection = (AMQConnection) getConnection();

        AMQSession session = (AMQSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        AMQDestination destination = (AMQDestination) session.createQueue(getTestQueueName());

        // Set no wait true so that we block the connection
        // Also set a different exchange class string so the attempt to declare
        // the exchange causes an exchange. 
        ExchangeDeclareBody body = session.getMethodRegistry().createExchangeDeclareBody(session.getTicket(), destination.getExchangeName(), new AMQShortString("NewTypeForException"),
                                                                                         destination.getExchangeName().toString().startsWith("amq."),
                                                                                         false, false, false, true, null);

        AMQFrame exchangeDeclare = body.generateFrame(session.getChannelId());

        try
        {
            // block our thread so that can times out
            connection.getProtocolHandler().syncWrite(exchangeDeclare, ExchangeDeclareOkBody.class);
        }
        catch (Exception e)
        {
            if (!(e instanceof AMQAuthenticationException))
            {
                fail("Cause was not AMQAuthenticationException. Was " + e.getClass() + ":" + e.getMessage());
            }
        }

        try
        {
            // Depending on if the notification thread has closed the connection
            // or not we may get an exception here when we attempt to close the
            // connection. If we do get one then it should be the same as above
            // an AMQAuthenticationException.
            connection.close();
        }
        catch (Exception e)
        {
            if (!(e instanceof AMQAuthenticationException))
            {
                fail("Cause was not AMQAuthenticationException. Was " + e.getClass() + ":" + e.getMessage());
            }
        }

    }
}
