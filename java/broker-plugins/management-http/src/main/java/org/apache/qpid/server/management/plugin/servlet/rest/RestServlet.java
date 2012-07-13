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
import java.net.SocketAddress;
import java.util.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.server.model.*;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;


public class RestServlet extends AbstractServlet
{
    private static final Logger LOGGER = Logger.getLogger(RestServlet.class);
    /**
     * An initialization parameter to specify hierarchy
     */
    private static final String HIERARCHY_INIT_PARAMETER = "hierarchy";

    public static final String DEPTH_PARAM = "depth";
    public static final String SORT_PARAM = "sort";

    public static final Set<String> RESERVED_PARAMS = new HashSet<String>(Arrays.asList(DEPTH_PARAM, SORT_PARAM));

    private Class<? extends ConfiguredObject>[] _hierarchy;

    private volatile boolean initializationRequired = false;

    public RestServlet()
    {
        super();
        initializationRequired = true;
    }

    public RestServlet(Broker broker, Class<? extends ConfiguredObject>... hierarchy)
    {
        super(broker);
        _hierarchy = hierarchy;
    }

    @Override
    public void init() throws ServletException
    {
        if (initializationRequired)
        {
            doInitialization();
            initializationRequired = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void doInitialization() throws ServletException
    {
        ServletConfig config = getServletConfig();
        String hierarchy = config.getInitParameter(HIERARCHY_INIT_PARAMETER);
        if (hierarchy != null && !"".equals(hierarchy))
        {
            List<Class<? extends ConfiguredObject>> classes = new ArrayList<Class<? extends ConfiguredObject>>();
            String[] hierarchyItems = hierarchy.split(",");
            for (String item : hierarchyItems)
            {
                Class<?> itemClass = null;
                try
                {
                    itemClass = Class.forName(item);
                }
                catch (ClassNotFoundException e)
                {
                    try
                    {
                        itemClass = Class.forName("org.apache.qpid.server.model." + item);
                    }
                    catch (ClassNotFoundException e1)
                    {
                        throw new ServletException("Unknown configured object class '" + item
                                + "' is specified in hierarchy for " + config.getServletName());
                    }
                }
                Class<? extends ConfiguredObject> clazz = (Class<? extends ConfiguredObject>)itemClass;
                classes.add(clazz);
            }
            Class<? extends ConfiguredObject>[] hierachyClasses = (Class<? extends ConfiguredObject>[])new Class[classes.size()];
            _hierarchy = classes.toArray(hierachyClasses);
        }
        else
        {
            _hierarchy = (Class<? extends ConfiguredObject>[])new Class[0];
        }
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

        Collection<ConfiguredObject> parents = Collections.singleton((ConfiguredObject) getBroker());
        Collection<ConfiguredObject> children = new ArrayList<ConfiguredObject>();

        Map<Class<? extends ConfiguredObject>, String> filters =
                new HashMap<Class<? extends ConfiguredObject>, String>();

        for(int i = 0; i < _hierarchy.length; i++)
        {
            if(i == 0 || Model.getChildTypes(_hierarchy[i - 1]).contains(_hierarchy[i]))
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
                            getAncestors(getConfiguredClass(),entry.getKey(), o);
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

        return filter(parents, request);
    }

    private Collection<ConfiguredObject> filter(Collection<ConfiguredObject> objects, HttpServletRequest request)
    {


        Map<String, Collection<String>> filters = new HashMap<String, Collection<String>>();

        for(String param : (Collection<String>) Collections.list(request.getParameterNames()))
        {
            if(!RESERVED_PARAMS.contains(param))
            {
                filters.put(param, Arrays.asList(request.getParameterValues(param)));
            }
        }

        if(filters.isEmpty())
        {
            return objects;
        }

        Collection<ConfiguredObject> filteredObj = new ArrayList<ConfiguredObject>(objects);

        Iterator<ConfiguredObject> iter = filteredObj.iterator();

        while(iter.hasNext())
        {
            ConfiguredObject obj = iter.next();
            for(Map.Entry<String, Collection<String>> entry : filters.entrySet())
            {
                Object value = obj.getAttribute(entry.getKey());
                if(!entry.getValue().contains(String.valueOf(value)))
                {
                    iter.remove();
                }
            }

        }

        return filteredObj;
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

    @Override
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

        // TODO - depth and sort special params, everything else should act as a filter
        if(request.getParameter(DEPTH_PARAM)!=null)
        {
            try
            {
                depth = Integer.parseInt(request.getParameter(DEPTH_PARAM));
            }
            catch (NumberFormatException e)
            {

            }
        }

        for(ConfiguredObject configuredObject : allObjects)
        {
            output.add(convertObjectToMap(configuredObject, getConfiguredClass(),depth));
        }

        final PrintWriter writer = response.getWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, output);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

    }

    private Class<? extends ConfiguredObject> getConfiguredClass()
    {
        return _hierarchy.length == 0 ? Broker.class : _hierarchy[_hierarchy.length-1];
    }

    @Override
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
            try
            {
                getBroker().createChild(_hierarchy[0], providedObject);
            }
            catch (RuntimeException e)
            {
                setResponseStatus(response, e);
                return;
            }

        }
        else
        {
            for(int i = 0; i < _hierarchy.length-1; i++)
            {
                objects[i] = new HashSet<ConfiguredObject>();
                if(i == 0)
                {
                    for(ConfiguredObject object : getBroker().getChildren(_hierarchy[0]))
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
            Class<? extends ConfiguredObject> objClass = getConfiguredClass();
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

            try
            {

                Collection<? extends ConfiguredObject> existingChildren = theParent.getChildren(objClass);
                for(ConfiguredObject obj: existingChildren)
                {
                    if((providedObject.containsKey("id") && String.valueOf(providedObject.get("id")).equals(obj.getId().toString()))
                       || (obj.getName().equals(providedObject.get("name")) && equalParents(obj, otherParents)))
                    {
                        doUpdate(obj, providedObject);
                    }
                }
                theParent.createChild(objClass, providedObject, otherParents);
            }
            catch (RuntimeException e)
            {
                setResponseStatus(response, e);
                return;
            }

        }
        response.setStatus(HttpServletResponse.SC_CREATED);
    }

    private void doUpdate(ConfiguredObject obj, Map<String, Object> providedObject)
    {
        for(Map.Entry<String,Object> entry : providedObject.entrySet())
        {
            obj.setAttribute(entry.getKey(), obj.getAttribute(entry.getKey()), entry.getValue());
        }
        //TODO - Implement.
    }

    private boolean equalParents(ConfiguredObject obj, ConfiguredObject[] otherParents)
    {
        if(otherParents == null || otherParents.length == 0)
        {
            return true;
        }
        return false;  //TODO - Implement.
    }

    private void setResponseStatus(HttpServletResponse response, RuntimeException e) throws IOException
    {
        if (e.getCause() instanceof AMQSecurityException)
        {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        else
        {
            LOGGER.warn("Unexpected exception is caught", e);

            // TODO
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        }
    }

    @Override
    protected void onDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);
        try
        {
            Collection<ConfiguredObject> allObjects = getObjects(request);
            for(ConfiguredObject o : allObjects)
            {
                o.setDesiredState(o.getActualState(), State.DELETED);
            }

            response.setStatus(HttpServletResponse.SC_OK);
        }
        catch(RuntimeException e)
        {
            setResponseStatus(response, e);
        }
    }
}
