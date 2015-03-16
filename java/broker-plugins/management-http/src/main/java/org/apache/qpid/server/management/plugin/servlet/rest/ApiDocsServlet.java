/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectAttribute;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.util.urlstreamhandler.data.Handler;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.util.DataUrlUtils;

public class ApiDocsServlet extends AbstractServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiDocsServlet.class);

    public static final String DEPTH_PARAM = "depth";
    public static final String OVERSIZE_PARAM = "oversize";
    public static final String ACTUALS_PARAM = "actuals";
    public static final String SORT_PARAM = "sort";
    public static final String INCLUDE_SYS_CONTEXT_PARAM = "includeSysContext";
    public static final String INHERITED_ACTUALS_PARAM = "inheritedActuals";
    public static final String EXTRACT_INITIAL_CONFIG_PARAM = "extractInitialConfig";
    public static final int SC_UNPROCESSABLE_ENTITY = 422;

    /**
     * Signifies that the agent wishes the servlet to set the Content-Disposition on the
     * response with the value attachment.  This filename will be derived from the parameter value.
     */
    public static final String CONTENT_DISPOSITION_ATTACHMENT_FILENAME_PARAM = "contentDispositionAttachmentFilename";

    public static final Set<String> RESERVED_PARAMS =
            new HashSet<>(Arrays.asList(DEPTH_PARAM,
                                        SORT_PARAM,
                                        OVERSIZE_PARAM,
                                        ACTUALS_PARAM,
                                        INCLUDE_SYS_CONTEXT_PARAM,
                                        EXTRACT_INITIAL_CONFIG_PARAM,
                                        INHERITED_ACTUALS_PARAM,
                                        CONTENT_DISPOSITION_ATTACHMENT_FILENAME_PARAM));
    private final Model _model;
    private final Collection<Class<? extends ConfiguredObject>> _types;

    private Class<? extends ConfiguredObject>[] _hierarchy;

    private static final Set<Character> VOWELS = new HashSet<>(Arrays.asList('a','e','i','o','u'));


    public ApiDocsServlet(final Model model, Class<? extends ConfiguredObject>... hierarchy)
    {
        super();
        _model = model;
        _hierarchy = hierarchy;
        _types = _model.getTypeRegistry().getTypeSpecialisations(getConfiguredClass());

    }

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);


        PrintWriter writer = response.getWriter();
        writePreamble(writer);
        writeHead(writer);
        writeUsage(writer, request);
        writeTypes(writer);
        writeAttributes(writer);
        writeFoot(writer);
    }

    private void writePreamble(final PrintWriter writer)
    {
        writer.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"");
        writer.println("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
        writer.println("<html>");
        writer.println("<body>");

    }

    private void writeHead(final PrintWriter writer)
    {
        writer.println("<head>");
        writer.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"/css/apidocs.css\">");
        writer.print("<title>");
        writer.print("Qpid API : " + getConfiguredClass().getSimpleName());
        writer.println("</title>");

        writer.println("</head>");
    }

    private void writeUsage(final PrintWriter writer, final HttpServletRequest request)
    {
        writer.println("<a name=\"usage\"><h1>Usage</h1></a>");
        writer.println("<table class=\"usage\">");
        writer.println("<tbody>");
        writer.print("<tr><th class=\"operation\">Read</th><td class=\"method\">GET</td><td class=\"path\">" + request.getServletPath()
                .replace("apidocs", "api"));

        for (final Class<? extends ConfiguredObject> category : _hierarchy)
        {
            writer.print("[/&lt;" + category.getSimpleName().toLowerCase() + " name or id&gt;");
        }
        for(int i = 0; i < _hierarchy.length; i++)
        {
            writer.print("] ");
        }
        writer.println("</td></tr>");

        writer.print("<tr><th class=\"operation\">Update</th><td class=\"method\">PUT or POST</td><td class=\"path\">"
                     + request.getServletPath().replace("apidocs", "api"));
        for (final Class<? extends ConfiguredObject> category : _hierarchy)
        {
            writer.print("/&lt;" + category.getSimpleName().toLowerCase() + " name or id&gt;");
        }

        if(_hierarchy.length>1)
        {
            writer.print(
                    "<tr><th class=\"operation\">Create</th><td class=\"method\">PUT or POST</td><td class=\"path\">"
                    + request.getServletPath().replace("apidocs", "api"));
            for (int i = 0; i < _hierarchy.length - 1; i++)
            {
                writer.print("/&lt;" + _hierarchy[i].getSimpleName().toLowerCase() + " name or id&gt;");
            }

            writer.print("<tr><th class=\"operation\">Delete</th><td class=\"method\">DELETE</td><td class=\"path\">"
                         + request.getServletPath().replace("apidocs", "api"));
            for (final Class<? extends ConfiguredObject> category : _hierarchy)
            {
                writer.print("/&lt;" + category.getSimpleName().toLowerCase() + " name or id&gt;");
            }
        }

        writer.println("</tbody>");
        writer.println("</table>");

    }


    private void writeTypes(final PrintWriter writer)
    {
        if(!_types.isEmpty() && !(_types.size() == 1 && getTypeName(_types.iterator().next()).trim().equals("")))
        {
            writer.println("<a name=\"types\"><h2>Types</h2></a>");
            writer.println("<table class=\"types\">");
            writer.println("<thead>");
            writer.println("<tr><th class=\"type\">Type</th><th class=\"description\">Description</th></tr>");
            writer.println("</thead>");

            writer.println("<tbody>");
            for (Class<? extends ConfiguredObject> type : _types)
            {
                writer.print("<tr><td class=\"type\">");
                writer.print(getTypeName(type));
                writer.print("</td><td class=\"description\">");
                writer.print(type.getAnnotation(ManagedObject.class).description());
                writer.println("</td></tr>");

            }
            writer.println("</tbody>");
        }

        writer.println("</table>");
    }

    private String getTypeName(final Class<? extends ConfiguredObject> type)
    {
        return type.getAnnotation(ManagedObject.class).type() == null
                            ? _model.getTypeRegistry().getTypeClass(type).getSimpleName()
                            : type.getAnnotation(ManagedObject.class).type();
    }

    private void writeAttributes(final PrintWriter writer)
    {
        writer.println("<a name=\"types\"><h2>Attributes</h2></a>");
        writer.println("<h2>Common Attributes</h2>");

        writeAttributesTable(writer, _model.getTypeRegistry().getAttributeTypes(getConfiguredClass()).values());

        for(Class<? extends ConfiguredObject> type : _types)
        {

            ManagedObject typeAnnotation = type.getAnnotation(ManagedObject.class);
            String typeName = typeAnnotation.type() == null ? _model.getTypeRegistry().getTypeClass(type).getSimpleName() : typeAnnotation.type();
            Collection<ConfiguredObjectAttribute<?, ?>> typeSpecificAttributes =
                    _model.getTypeRegistry().getTypeSpecificAttributes(type);
            if(!typeSpecificAttributes.isEmpty())
            {
                writer.println("<h2><span class=\"type\">"+typeName+"</span> Specific Attributes</h2>");
                writeAttributesTable(writer, typeSpecificAttributes);
            }


        }

    }

    private void writeAttributesTable(final PrintWriter writer,
                                      final Collection<ConfiguredObjectAttribute<?, ?>> attributeTypes)
    {
        writer.println("<table class=\"attributes\">");
        writer.println("<thead>");
        writer.println("<tr><th class=\"name\">Attribute Name</th><th class=\"type\">Type</th><th class=\"description\">Description</th></tr>");
        writer.println("</thead>");
        writer.println("<tbody>");

        for(ConfiguredObjectAttribute attribute : attributeTypes)
        {
            if(!attribute.isDerived())
            {
                writer.println("<tr><td class=\"name\">"
                               + attribute.getName()
                               + "</td><td class=\"type\">"
                               + renderType(attribute)
                               + "</td class=\"description\"><td>"
                               + attribute.getDescription()
                               + "</td></tr>");
            }
        }
        writer.println("</tbody>");

        writer.println("</table>");

    }

    private String renderType(final ConfiguredObjectAttribute attribute)
    {
        final Class type = attribute.getType();
        if(Number.class.isAssignableFrom(type))
        {
            return "number";
        }
        else if(Enum.class.isAssignableFrom(type))
        {
            return "<span title=\"enum: " + EnumSet.allOf(type) + "\">string</span>";
        }
        else if(Boolean.class == type)
        {
            return "boolean";
        }
        else if(String.class == type)
        {
            return "string";
        }
        else if(UUID.class == type)
        {
            return "<span title=\"\">string</span>";
        }
        else if(List.class.isAssignableFrom(type))
        {
            // TODO - generate a description of the type in the array
            return "array";
        }
        else if(Map.class.isAssignableFrom(type))
        {
            // TODO - generate a description of the type in the object
            return "object";
        }
        else if(ConfiguredObject.class.isAssignableFrom(type))
        {
            return "<span title=\"name or id of a" + (VOWELS.contains(type.getSimpleName().toLowerCase().charAt(0)) ? "n " : " ") + type.getSimpleName() + "\">string</span>";
        }
        return type.getSimpleName();
    }

    private void writeFoot(final PrintWriter writer)
    {
        writer.println("</body>");
        writer.println("</html>");
    }
    private Class<? extends ConfiguredObject> getConfiguredClass()
    {
        return _hierarchy.length == 0 ? Broker.class : _hierarchy[_hierarchy.length-1];
    }


    private int getIntParameterFromRequest(final HttpServletRequest request,
                                           final String paramName,
                                           final int defaultValue)
    {
        int intValue = defaultValue;
        final String stringValue = request.getParameter(paramName);
        if(stringValue!=null)
        {
            try
            {
                intValue = Integer.parseInt(stringValue);
            }
            catch (NumberFormatException e)
            {
                LOGGER.warn("Could not parse " + stringValue + " as integer for parameter " + paramName);
            }
        }
        return intValue;
    }

    private boolean getBooleanParameterFromRequest(HttpServletRequest request, final String paramName)
    {
        return Boolean.parseBoolean(request.getParameter(paramName));
    }


}
