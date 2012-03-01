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

package org.apache.qpid.jca.example.web;
import java.io.IOException;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

import org.apache.qpid.jca.example.ejb.QpidTestLocal;
import org.apache.qpid.jca.example.ejb.QpidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public class QpidTestServlet extends HttpServlet
{
    private static final Logger _log = LoggerFactory.getLogger(QpidTestServlet.class);

    private static final String DEFAULT_MESSAGE = "Hello, World!";
    private static final int DEFAULT_COUNT = 1;
    private static final boolean DEFAULT_TOPIC = false;
    private static final boolean DEFAULT_XA = false;
    private static final boolean DEFAULT_SAY_GOODBYE = true;

    @Resource(@jndi.scheme@="@qpid.xacf.jndi.name@")
    private ConnectionFactory _connectionFactory;

    @Resource(@jndi.scheme@="HelloQueue")
    private Destination _queue;

    @Resource(@jndi.scheme@="HelloTopic")
    private Destination _topic;

    @EJB
    private QpidTestLocal ejb;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        InitialContext ctx = null;
        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;
        UserTransaction ut = null;
        boolean useXA = false;
        boolean rollback = false;

        try
        {
            String content = (req.getParameter("message") == null) ? DEFAULT_MESSAGE : req.getParameter("message");
            boolean useEJB = (req.getParameter("useEJB") == null) ? false : Boolean.valueOf(req.getParameter("useEJB"));
            int count = (req.getParameter("count") == null) ? DEFAULT_COUNT : Integer.valueOf(req.getParameter("count"));
            boolean useTopic = (req.getParameter("useTopic") == null) ? DEFAULT_TOPIC : Boolean.valueOf(req.getParameter("useTopic"));
            useXA = (req.getParameter("useXA") == null) ? DEFAULT_XA : Boolean.valueOf(req.getParameter("useXA"));
            ctx = new InitialContext();
            boolean sayGoodBye = (req.getParameter("sayGoodBye") == null) ? DEFAULT_SAY_GOODBYE : Boolean.valueOf(req.getParameter("sayGoodBye"));

            _log.debug("Environment: ");
            _log.debug("Message content: " + content);
            _log.debug("Message count:" + count);
            _log.debug("Protocol: " + ((useEJB) ? "EJB" : "JMS"));
            _log.debug("Destination Type: " + ((useTopic) ? "Topic" : "Queue"));
            _log.debug("Using XA: " + useXA);
            _log.debug("Say GoodBye: ", sayGoodBye);

            resp.getOutputStream().println("Environment: ");
            resp.getOutputStream().println("Message content: " + content);
            resp.getOutputStream().println("Message count:" + count);
            resp.getOutputStream().println("Protocol: " + ((useEJB) ? "EJB" : "JMS"));
            resp.getOutputStream().println("Destination Type: " + ((useTopic) ? "Topic" : "Queue"));
            resp.getOutputStream().println("Using XA: " + useXA);
            resp.getOutputStream().println("Say GoodBye: " + sayGoodBye);

            if(useEJB)
            {
                ejb.testQpidAdapter(content, count, useTopic, false, sayGoodBye);
            }
            else
            {
                if(useXA)
                {
                    ut = (UserTransaction)ctx.lookup("java:comp/UserTransaction");
                    ut.begin();
                }

                connection = _connectionFactory.createConnection();
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                messageProducer = (useTopic) ? session.createProducer(_topic) : session.createProducer(_queue);

                for(int i = 0; i < count; i++)
                {
                    TextMessage message = session.createTextMessage(content);
                    message.setBooleanProperty("say.goodbye", sayGoodBye);
                    messageProducer.send(message);
                }

            }

            resp.getOutputStream().println("Sent " + count + " messages with content '" + content + "'");
            resp.getOutputStream().flush();

        }
        catch(Exception e)
        {

            if(useXA && ut != null)
            {
                try
                {
                    rollback = true;
                    ut.setRollbackOnly();
                }
                catch(Exception ex)
                {
                    _log.error(ex.getMessage(), ex);
                    throw new ServletException(ex.getMessage(), ex);
                }
            }

            _log.error(e.getMessage(), e);
            throw new ServletException(e.getMessage(), e);
        }
        finally
        {
            if(useXA && ut != null)
            {
                try
                {
                    if(rollback)
                    {
                        ut.rollback();
                    }
                    else
                    {
                        ut.commit();
                    }
                }
                catch(Exception e)
                {
                    _log.error(e.getMessage(), e);
                    throw new ServletException(e.getMessage(), e);

                }
            }

            QpidUtil.closeResources(session, connection, ctx);
        }
    }



}


