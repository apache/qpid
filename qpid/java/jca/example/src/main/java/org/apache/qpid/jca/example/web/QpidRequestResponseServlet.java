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
import java.lang.Thread;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
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

import org.apache.qpid.jca.example.ejb.QpidTest;
import org.apache.qpid.jca.example.ejb.QpidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public class QpidRequestResponseServlet extends HttpServlet
{
    private static final Logger _log = LoggerFactory.getLogger(QpidTestServlet.class);

    private static final String DEFAULT_MESSAGE = "Hello, World!";
    private static final int DEFAULT_COUNT = 1;
    private static final boolean DEFAULT_XA = false;
    private static final boolean DEFAULT_TX = false;
    private static final boolean USE_TMP_QUEUE = false;

    @Resource(@jndi.scheme@="@qpid.xacf.jndi.name@")
    private ConnectionFactory _connectionFactory;

    @Resource(@jndi.scheme@="@qpid.request.queue.jndi.name@")
    private Destination _queue;

    @Resource(@jndi.scheme@="@qpid.response.queue.jndi.name@")
    private Destination _responseQueue;


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
        boolean useTx = false;
        MessageConsumer messageConsumer = null;
        long startTime = 0;

        try
        {
            String content = (req.getParameter("message") == null) ? DEFAULT_MESSAGE : req.getParameter("message");
            int count = (req.getParameter("count") == null) ? DEFAULT_COUNT : Integer.valueOf(req.getParameter("count"));
            useXA = (req.getParameter("useXA") == null) ? DEFAULT_XA : Boolean.valueOf(req.getParameter("useXA"));
            useTx = (req.getParameter("useTx") == null) ? DEFAULT_TX : Boolean.valueOf(req.getParameter("useTx"));

            ctx = new InitialContext();

            _log.debug("Environment: ");
            _log.debug("Message content: " + content);
            _log.debug("Message count:" + count);
            _log.debug("Using XA: " + useXA);

            resp.getOutputStream().println("Environment: ");
            resp.getOutputStream().println("Message content: " + content);
            resp.getOutputStream().println("Message count:" + count);
            resp.getOutputStream().println("Using XA: " + useXA);

            try
            {

                connection = _connectionFactory.createConnection();

                if(useXA)
                {
                    ut = (UserTransaction)ctx.lookup("java:comp/UserTransaction");
                    ut.begin();
                    useTx = false;
                }

                session = (useXA) ? connection.createSession(false, Session.AUTO_ACKNOWLEDGE) : connection.createSession(useTx, Session.AUTO_ACKNOWLEDGE);
                messageProducer = session.createProducer(_queue);

                startTime = System.currentTimeMillis();

                for(int i = 0; i < count; i++)
                {
                    TextMessage message = session.createTextMessage(content);
                    message.setJMSReplyTo(_responseQueue);
                    messageProducer.send(message);
                }

            }
            catch(Exception e)
            {
                rollback = true;

                if(useXA && ut != null)
                {
                    try
                    {
                        ut.setRollbackOnly();
                    }
                    catch(Exception ex)
                    {
                        _log.error(ex.getMessage(), ex);
                        throw new ServletException(ex.getMessage(), ex);
                    }
                }
            }
            finally
            {
                try
                {
                    if(useXA && ut != null)
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
                    if(useTx && !useXA)
                    {
                        if(rollback)
                        {
                            session.rollback();
                        }
                        else
                        {
                            session.commit();
                        }
                    }
                }
                catch(Exception e)
                {
                    _log.error(e.getMessage(), e);
                    throw new ServletException(e.getMessage(), e);
                }

                QpidUtil.closeResources(messageProducer, session);
            }

            resp.getOutputStream().println("Sent " + count + " messages with content '" + content + "'");
            resp.getOutputStream().flush();

            int ackMode = Session.AUTO_ACKNOWLEDGE;
            rollback = false;

            if(useXA)
            {
                ut.begin();
            }

            session = (useXA) ? connection.createSession(false, Session.AUTO_ACKNOWLEDGE) : connection.createSession(useTx, Session.AUTO_ACKNOWLEDGE);
            messageConsumer = session.createConsumer(_responseQueue);
            connection.start();

            for(int i = 0; i < count; i++)
            {
                TextMessage message = (TextMessage)messageConsumer.receive(5000);

                if(message != null)
                {
                    message.acknowledge();
                    content = message.getText();

                }
            }

            startTime = System.currentTimeMillis() - startTime;
            resp.getOutputStream().println("Received " + count + " messages with content '" + content + "'");
            resp.getOutputStream().println("Total process time " + startTime);
        }
        catch(Exception e)
        {
            rollback = true;

            if(useXA && ut != null)
            {
                try
                {
                    ut.setRollbackOnly();
                }
                catch(Exception ex)
                {
                    _log.error(ex.getMessage(), ex);
                    throw new ServletException(ex.getMessage(), ex);
                }
            }
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

            if(useTx && !useXA)
            {
                try
                {
                    if(rollback)
                    {
                        session.rollback();
                    }
                    else
                    {
                        session.commit();
                    }
                }
                catch(Exception e)
                {
                    _log.error(e.getMessage(), e);
                    throw new ServletException(e.getMessage(), e);
                }
            }

            QpidUtil.closeResources(messageProducer, session);
        }
    }

}


