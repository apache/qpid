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
package org.apache.qpid.jca.example.ejb;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
public class QpidTestBean implements QpidTestRemote, QpidTestLocal
{

    private static final Logger _log = LoggerFactory.getLogger(QpidTestBean.class);

    @Resource(@jndi.scheme@="@qpid.xacf.jndi.name@")
    private ConnectionFactory _connectionFactory;

    @Resource(@jndi.scheme@="HelloQueue")
    private Destination _queue;

    @Resource(@jndi.scheme@="HelloTopic")
    private Destination _topic;

    @Override
    public void testQpidAdapter(String content) throws Exception
    {
        testQpidAdapter(content, 1);
    }

    @Override
    public void testQpidAdapter(String content, int count) throws Exception
    {
        testQpidAdapter(content, count, false);
    }

    public void testQpidAdapter(final String content, int count, boolean useTopic) throws Exception
    {
        testQpidAdapter(content, count, useTopic, false, false);
	}

    @Override
    public void testQpidAdapter(String content, int count, boolean useTopic,
            boolean respond, boolean sayGoodbye) throws Exception
    {
        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;

        _log.info("Sending " + count + " message(s) to MDB with content " + content);

        try
        {
            connection = _connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            messageProducer = (useTopic) ? session.createProducer(_topic) : session.createProducer(_queue);

            for(int i = 0; i < count; i++)
            {
                TextMessage message = session.createTextMessage(content);
                message.setBooleanProperty("say.goodbye", sayGoodbye);
                messageProducer.send(message);
            }

        }
        catch(Exception e)
        {
           _log.error(e.getMessage(), e);
           throw e;
        }
        finally
        {
            QpidUtil.closeResources(messageProducer, session, connection);
        }
    }

}
