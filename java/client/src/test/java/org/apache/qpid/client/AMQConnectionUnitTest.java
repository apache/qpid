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
package org.apache.qpid.client;

import junit.framework.TestCase;

import org.apache.qpid.AMQInvalidArgumentException;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import java.util.concurrent.atomic.AtomicReference;

public class AMQConnectionUnitTest extends TestCase
{

    public void testExceptionReceived()
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";
        AMQInvalidArgumentException expectedException = new AMQInvalidArgumentException("Test", null);
        final AtomicReference<JMSException> receivedException = new AtomicReference<JMSException>();
        try
        {
            MockAMQConnection connection = new MockAMQConnection(url);
            connection.setExceptionListener(new ExceptionListener()
            {

                @Override
                public void onException(JMSException jmsException)
                {
                    receivedException.set(jmsException);
                }
            });
            connection.exceptionReceived(expectedException);
        }
        catch (Exception e)
        {
            fail("Failure to test exceptionRecived:" + e.getMessage());
        }
        JMSException exception = receivedException.get();
        assertNotNull("Expected JMSException but got null", exception);
        assertEquals("JMSException error code is incorrect", Integer.toString(expectedException.getErrorCode().getCode()), exception.getErrorCode());
        assertNotNull("Expected not null message for JMSException", exception.getMessage());
        assertTrue("JMSException error message is incorrect",  exception.getMessage().contains(expectedException.getMessage()));
        assertEquals("JMSException linked exception is incorrect", expectedException, exception.getLinkedException());
    }

}
