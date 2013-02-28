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

import org.apache.log4j.Logger;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessControlException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class SaslServlet extends AbstractServlet
{

    private static final Logger LOGGER = Logger.getLogger(SaslServlet.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ATTR_RANDOM = "SaslServlet.Random";
    private static final String ATTR_ID = "SaslServlet.ID";
    private static final String ATTR_SASL_SERVER = "SaslServlet.SaslServer";
    private static final String ATTR_EXPIRY = "SaslServlet.Expiry";
    private static final long SASL_EXCHANGE_EXPIRY = 1000L;

    public SaslServlet()
    {
        super();
    }

    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws
                                                                                   ServletException,
                                                                                   IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        HttpSession session = request.getSession();
        getRandom(session);

        SubjectCreator subjectCreator = getSubjectCreator(request);
        String[] mechanisms = subjectCreator.getMechanisms().split(" ");
        Map<String, Object> outputObject = new LinkedHashMap<String, Object>();

        final Subject subject = getAuthorisedSubjectFromSession(session);
        if(subject != null)
        {
            Principal principal = AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(subject);
            outputObject.put("user", principal.getName());
        }
        else if (request.getRemoteUser() != null)
        {
            outputObject.put("user", request.getRemoteUser());
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
    protected void doPostWithSubjectAndActor(final HttpServletRequest request, final HttpServletResponse response) throws IOException
    {
        checkSaslAuthEnabled(request);

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

            SubjectCreator subjectCreator = getSubjectCreator(request);

            if(mechanism != null)
            {
                if(id == null)
                {
                    if(LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Creating SaslServer for mechanism: " + mechanism);
                    }
                    SaslServer saslServer = subjectCreator.createSaslServer(mechanism, request.getServerName(), null/*TODO*/);
                    evaluateSaslResponse(request, response, session, saslResponse, saslServer, subjectCreator);
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
                        evaluateSaslResponse(request, response, session, saslResponse, saslServer, subjectCreator);
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
            LOGGER.error("Error processing SASL request", e);
            throw e;
        }
        catch(RuntimeException e)
        {
            LOGGER.error("Error processing SASL request", e);
            throw e;
        }
    }

    private void checkSaslAuthEnabled(HttpServletRequest request)
    {
        boolean saslAuthEnabled;
        HttpManagement management = getManagement();
        if (request.isSecure())
        {
            saslAuthEnabled = management.isHttpsSaslAuthenticationEnabled();
        }
        else
        {
            saslAuthEnabled = management.isHttpSaslAuthenticationEnabled();
        }

        if (!saslAuthEnabled)
        {
            throw new RuntimeException("Sasl authentication disabled.");
        }
    }

    private void evaluateSaslResponse(final HttpServletRequest request,
                                      final HttpServletResponse response,
                                      final HttpSession session, final String saslResponse, final SaslServer saslServer, SubjectCreator subjectCreator) throws IOException
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
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

            return;
        }

        if(saslServer.isComplete())
        {
            Subject subject = subjectCreator.createSubjectWithGroups(saslServer.getAuthorizationID());

            try
            {
                authoriseManagement(request, subject);
            }
            catch (AccessControlException ace)
            {
                sendError(response, HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            setAuthorisedSubjectInSession(subject, request, session);
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
