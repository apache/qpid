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
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractServlet extends HttpServlet
{
    private Subject _subject;
    private final Broker _broker;
    private Class<? extends ConfiguredObject>[] _hierarchy;

    protected AbstractServlet(Broker broker, Class<? extends ConfiguredObject>... hierarchy)
    {
        _broker = broker;
        _hierarchy = hierarchy;
    }

    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException
    {
        setAuthorizedSubject(request);
        try
        {
            onGet(request, resp);
        }
        finally
        {
            clearAuthorizedSubject();
        }
    }

    private void clearAuthorizedSubject()
    {
        _subject = null;
        org.apache.qpid.server.security.SecurityManager.setThreadSubject(null);
    }


    private void setAuthorizedSubject(HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        Subject subject = (Subject) session.getAttribute("subject");

        if(subject == null)
        {
            Principal principal = request.getUserPrincipal();
            if(principal != null)
            {
                subject = new Subject(false, Collections.singleton(principal),Collections.emptySet(),
                                      Collections.emptySet());
            }
            else
            {
                String header = request.getHeader("Authorization");
                if (header != null)
                {
                    String[] tokens = header.split("\\s");
                    if(tokens.length >= 2
                       && "BASIC".equalsIgnoreCase(tokens[0]))
                    {
                        String[] credentials = (new String(Base64.decodeBase64(tokens[1].getBytes()))).split(":",2);
                        if(credentials.length == 2)
                        {


                            AuthenticationManager authenticationManager =
                                    ApplicationRegistry.getInstance().getAuthenticationManager();
                            AuthenticationResult authResult =
                                    authenticationManager.authenticate(credentials[0], credentials[1]);
                            subject = authResult.getSubject();

                        }
                    }
                }
            }
        }
        _subject = subject;
        org.apache.qpid.server.security.SecurityManager.setThreadSubject(subject);

    }

    protected Subject getSubject()
    {
        return _subject;
    }

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        setAuthorizedSubject(req);
        try
        {
            onPost(req, resp);
        }
        finally
        {
            clearAuthorizedSubject();
        }

    }

    protected void onPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        super.doPost(req, resp);
    }

    @Override
    protected final void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        setAuthorizedSubject(req);
        try
        {
            onPut(req, resp);

        }
        finally
        {
            clearAuthorizedSubject();
        }
    }

    @Override
    protected final void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        setAuthorizedSubject(req);
        try
        {
            onDelete(req, resp);
        }
        finally
        {
            clearAuthorizedSubject();
        }
    }

    protected void onDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        super.doDelete(req, resp);
    }


    protected Broker getBroker()
    {
        return _broker;
    }

    protected Collection<ConfiguredObject> getObjects(HttpServletRequest request)
    {
        List<String> names = new ArrayList<String>();
        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String path = request.getPathInfo().substring(1);
            names.addAll(Arrays.asList(path.split("/")));

            if(names.size() > _hierarchy.length)
            {
                throw new IllegalArgumentException("Too many entries in path");
            }
        }

        Collection<ConfiguredObject> parents = Collections.singleton((ConfiguredObject) _broker);
        Collection<ConfiguredObject> children = new ArrayList<ConfiguredObject>();

        Map<Class<? extends ConfiguredObject>, String> filters =
                new HashMap<Class<? extends ConfiguredObject>, String>();

        for(int i = 0; i < _hierarchy.length; i++)
        {
            if(i == 0 || Model.getChildTypes(_hierarchy[i-1]).contains(_hierarchy[i]))
            {

                for(ConfiguredObject parent : parents)
                {
                    if(names.size() > i
                       && names.get(i) != null
                       && !names.get(i).equals("*")
                       && names.get(i).trim().length() != 0)
                    {
                        for(ConfiguredObject child : parent.getChildren(_hierarchy[i]))
                        {
                            if(child.getName().equals(names.get(i)))
                            {
                                children.add(child);
                            }
                        }
                    }
                    else
                    {
                        children.addAll(parent.getChildren(_hierarchy[i]));
                    }
                }
            }
            else
            {
                children = parents;
                if(names.size() > i
                   && names.get(i) != null
                   && !names.get(i).equals("*")
                   && names.get(i).trim().length() != 0)
                {
                    filters.put(_hierarchy[i], names.get(i));
                }
            }

            parents = children;
            children = new ArrayList<ConfiguredObject>();
        }

        if(!filters.isEmpty())
        {
            Collection<ConfiguredObject> potentials = parents;
            parents = new ArrayList<ConfiguredObject>();

            for(ConfiguredObject o : potentials)
            {

                boolean match = true;

                for(Map.Entry<Class<? extends ConfiguredObject>, String> entry : filters.entrySet())
                {
                    Collection<? extends ConfiguredObject> ancestors =
                            getAncestors(_hierarchy[_hierarchy.length-1],entry.getKey(), o);
                    match = false;
                    for(ConfiguredObject ancestor : ancestors)
                    {
                        if(ancestor.getName().equals(entry.getValue()))
                        {
                            match = true;
                            break;
                        }
                    }
                    if(!match)
                    {
                        break;
                    }
                }
                if(match)
                {
                    parents.add(o);
                }

            }
        }

        return parents;
    }

    private Collection<? extends ConfiguredObject> getAncestors(Class<? extends ConfiguredObject> childType,
                                                                Class<? extends ConfiguredObject> ancestorType,
                                                                ConfiguredObject child)
    {
        Collection<ConfiguredObject> ancestors = new HashSet<ConfiguredObject>();
        Collection<Class<? extends ConfiguredObject>> parentTypes = Model.getParentTypes(childType);

        for(Class<? extends ConfiguredObject> parentClazz : parentTypes)
        {
            if(parentClazz == ancestorType)
            {
                ConfiguredObject parent = child.getParent(parentClazz);
                if(parent != null)
                {
                    ancestors.add(parent);
                }
            }
            else
            {
                ConfiguredObject parent = child.getParent(parentClazz);
                if(parent != null)
                {
                    ancestors.addAll(getAncestors(parentClazz, ancestorType, parent));
                }
            }
        }

        return ancestors;
    }


    protected Map<String, Object> convertObjectToMap(final ConfiguredObject confObject,
                                                     Class<? extends  ConfiguredObject> clazz,
                                                     int depth)
    {
        Map<String, Object> object = new LinkedHashMap<String, Object>();

        for(String name : confObject.getAttributeNames())
        {
            Object value = confObject.getAttribute(name);
            if(value != null)
            {
                object.put(name, value);
            }
        }

        Statistics statistics = confObject.getStatistics();
        Map<String, Object> statMap = new HashMap<String, Object>();
        for(String name : statistics.getStatisticNames())
        {
            Object value = statistics.getStatistic(name);
            if(value != null)
            {
                statMap.put(name, value);
            }
        }

        if(!statMap.isEmpty())
        {
            object.put("statistics", statMap);
        }

        if(depth > 0)
        {
            for(Class<? extends ConfiguredObject> childClass : Model.getChildTypes(clazz))
            {
                Collection<? extends ConfiguredObject> children = confObject.getChildren(childClass);
                if(children != null)
                {
                    List<Map<String, Object>> childObjects = new ArrayList<Map<String, Object>>();

                    for(ConfiguredObject child : children)
                    {
                        childObjects.add(convertObjectToMap(child, childClass, depth-1));
                    }

                    if(!childObjects.isEmpty())
                    {
                        object.put(childClass.getSimpleName().toLowerCase()+"s",childObjects);
                    }
                }
            }
        }
        return object;
    }

    protected void onGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        Collection<ConfiguredObject> allObjects = getObjects(request);

        @SuppressWarnings("unchecked")
        Map params = new HashMap(request.getParameterMap());

        int depth = 1;
        try
        {
            depth = Integer.parseInt(String.valueOf(params.remove("depth")));
        }
        catch (NumberFormatException e)
        {
            // Ignore
        }

        List<Map<String, Object>> output = new ArrayList<Map<String, Object>>();

        if(!params.isEmpty())
        {
            // TODO - depth and sort special params, everything else should act as a filter
        }

        for(ConfiguredObject configuredObject : allObjects)
        {
            output.add(convertObjectToMap(configuredObject, _hierarchy[_hierarchy.length-1],depth));
        }

        final PrintWriter writer = response.getWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, output);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

    }

    protected void onPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");

        ObjectMapper mapper = new ObjectMapper();

        @SuppressWarnings("unchecked")
        Map<String,Object> providedObject = mapper.readValue(request.getInputStream(), LinkedHashMap.class);


        List<String> names = new ArrayList<String>();
        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String path = request.getPathInfo().substring(1);
            names.addAll(Arrays.asList(path.split("/")));

            if(names.size() != _hierarchy.length)
            {
                throw new IllegalArgumentException("Path to object to create must be fully specified");
            }
        }


        providedObject.put("name", names.get(names.size()-1));

        @SuppressWarnings("unchecked")
        Collection<ConfiguredObject>[] objects = new Collection[_hierarchy.length];
        if(_hierarchy.length == 1)
        {
            _broker.createChild(_hierarchy[0], providedObject);
        }
        else
        {
            for(int i = 0; i < _hierarchy.length-1; i++)
            {
                objects[i] = new HashSet<ConfiguredObject>();
                if(i == 0)
                {
                    for(ConfiguredObject object : _broker.getChildren(_hierarchy[0]))
                    {
                        if(object.getName().equals(names.get(0)))
                        {
                            objects[0].add(object);
                            break;
                        }
                    }
                }
                else
                {
                    for(int j = i-1; j >=0; j--)
                    {
                        if(Model.getChildTypes(_hierarchy[j]).contains(_hierarchy[i]))
                        {
                            for(ConfiguredObject parent : objects[j])
                            {
                                for(ConfiguredObject object : parent.getChildren(_hierarchy[i]))
                                {
                                    if(object.getName().equals(names.get(i)))
                                    {
                                        objects[i].add(object);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

            }
            List<ConfiguredObject> parents = new ArrayList<ConfiguredObject>();
            Class<? extends ConfiguredObject> objClass = _hierarchy[_hierarchy.length - 1];
            Collection<Class<? extends ConfiguredObject>> parentClasses = Model.getParentTypes(objClass);
            for(int i = _hierarchy.length-2; i >=0 ; i--)
            {
                if(parentClasses.contains(_hierarchy[i]))
                {
                    if(objects[i].size() == 1)
                    {
                        parents.add(objects[i].iterator().next());
                    }
                    else
                    {
                        throw new IllegalArgumentException("Cannot deduce parent of class "
                                                           + _hierarchy[i].getSimpleName());
                    }
                }

            }
            ConfiguredObject theParent = parents.remove(0);
            ConfiguredObject[] otherParents = parents.toArray(new ConfiguredObject[parents.size()]);

            theParent.createChild(objClass, providedObject, otherParents);
        }
    }

}
