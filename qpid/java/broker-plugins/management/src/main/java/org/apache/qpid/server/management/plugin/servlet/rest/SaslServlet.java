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
package org.apache.qpid.server.management.plugin.servlet.rest;

import org.apache.commons.codec.binary.Base64;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class SaslServlet extends AbstractServlet
{

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ATTR_RANDOM = "SaslServlet.Random";
    private static final String ATTR_ID = "SaslServlet.ID";
    private static final String ATTR_SASL_SERVER = "SaslServlet.SaslServer";
    private static final String ATTR_EXPIRY = "SaslServlet.Expiry";
    private static final long SASL_EXCHANGE_EXPIRY = 1000L;


    public SaslServlet(Broker broker)
    {
        super(broker);
    }

    protected void onGet(HttpServletRequest request, HttpServletResponse response) throws
                                                                                   ServletException,
                                                                                   IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        HttpSession session = request.getSession();
        Random rand = getRandom(session);

        AuthenticationManager authManager = ApplicationRegistry.getInstance().getAuthenticationManager();
        String[] mechanisms = authManager.getMechanisms().split(" ");
        Map<String, Object> outputObject = new LinkedHashMap<String, Object>();
        final Subject subject = (Subject) session.getAttribute("subject");
        if(subject != null)
        {
            final Principal principal = subject.getPrincipals().iterator().next();
            outputObject.put("user", principal.getName());
        }

        outputObject.put("mechanisms", (Object) mechanisms);

        final PrintWriter writer = response.getWriter();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, outputObject);

    }

    private Random getRandom(final HttpSession session)
    {
        Random rand = (Random) session.getAttribute(ATTR_RANDOM);
        if(rand == null)
        {
            synchronized (SECURE_RANDOM)
            {
                rand = new Random(SECURE_RANDOM.nextLong());
            }
            session.setAttribute(ATTR_RANDOM, rand);
        }
        return rand;
    }


    @Override
    protected void onPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException
    {
        try
        {
        response.setContentType("application/json");
        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader("Expires", 0);

        HttpSession session = request.getSession();

        String mechanism = request.getParameter("mechanism");
        String id = request.getParameter("id");
        String saslResponse = request.getParameter("response");

        AuthenticationManager authManager = ApplicationRegistry.getInstance().getAuthenticationManager();

        if(mechanism != null)
        {
            if(id == null)
            {
                SaslServer saslServer = authManager.createSaslServer(mechanism, request.getServerName());
                evaluateSaslResponse(response, session, saslResponse, saslServer);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                session.removeAttribute(ATTR_ID);
                session.removeAttribute(ATTR_SASL_SERVER);
                session.removeAttribute(ATTR_EXPIRY);

            }

        }
        else
        {
            if(id != null)
            {
                if(id.equals(session.getAttribute(ATTR_ID)) && System.currentTimeMillis() < (Long) session.getAttribute(ATTR_EXPIRY))
                {
                    SaslServer saslServer = (SaslServer) session.getAttribute(ATTR_SASL_SERVER);
                    evaluateSaslResponse(response, session, saslResponse, saslServer);

                }
                else
                {
                    response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                    session.removeAttribute(ATTR_ID);
                    session.removeAttribute(ATTR_SASL_SERVER);
                    session.removeAttribute(ATTR_EXPIRY);
                }
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                session.removeAttribute(ATTR_ID);
                session.removeAttribute(ATTR_SASL_SERVER);
                session.removeAttribute(ATTR_EXPIRY);

            }
        }
        }
        catch(IOException e)
        {
            e.printStackTrace();
            throw e;
        }
        catch(RuntimeException e)
        {
            e.printStackTrace();
            throw e;
        }

    }

    private void evaluateSaslResponse(final HttpServletResponse response,
                                      final HttpSession session,
                                      final String saslResponse, final SaslServer saslServer) throws IOException
    {
        final String id;
        byte[] challenge;
        try
        {
            challenge  = saslServer.evaluateResponse(saslResponse == null ? new byte[0] : Base64.decodeBase64(saslResponse.getBytes()));
        }
        catch(SaslException e)
        {

            session.removeAttribute(ATTR_ID);
            session.removeAttribute(ATTR_SASL_SERVER);
            session.removeAttribute(ATTR_EXPIRY);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            return;
        }

        if(saslServer.isComplete())
        {
            final Subject subject = new Subject();
            subject.getPrincipals().add(new UsernamePrincipal(saslServer.getAuthorizationID()));
            session.setAttribute("subject", subject);
            session.removeAttribute(ATTR_ID);
            session.removeAttribute(ATTR_SASL_SERVER);
            session.removeAttribute(ATTR_EXPIRY);

            response.setStatus(HttpServletResponse.SC_OK);


        }
        else
        {
            Random rand = getRandom(session);
            id = String.valueOf(rand.nextLong());
            session.setAttribute(ATTR_ID, id);
            session.setAttribute(ATTR_SASL_SERVER, saslServer);
            session.setAttribute(ATTR_EXPIRY, System.currentTimeMillis() + SASL_EXCHANGE_EXPIRY);

            response.setStatus(HttpServletResponse.SC_OK);

            Map<String, Object> outputObject = new LinkedHashMap<String, Object>();
            outputObject.put("id", id);
            outputObject.put("challenge", new String(Base64.encodeBase64(challenge)));

            final PrintWriter writer = response.getWriter();

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
            mapper.writeValue(writer, outputObject);

        }
    }
}
