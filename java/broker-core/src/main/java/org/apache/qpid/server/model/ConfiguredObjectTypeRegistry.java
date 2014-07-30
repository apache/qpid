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
package org.apache.qpid.server.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.qpid.server.plugin.ConfiguredObjectRegistration;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.util.Strings;

public class ConfiguredObjectTypeRegistry
{
    private static final Logger LOGGER = Logger.getLogger(ConfiguredObjectTypeRegistry.class);

    private static final Comparator<ConfiguredObjectAttributeOrStatistic<?,?>> NAME_COMPARATOR = new Comparator<ConfiguredObjectAttributeOrStatistic<?, ?>>()
    {
        @Override
        public int compare(final ConfiguredObjectAttributeOrStatistic<?, ?> left,
                           final ConfiguredObjectAttributeOrStatistic<?, ?> right)
        {
            return left.getName().compareTo(right.getName());
        }
    };


    private static final Map<Class<? extends ConfiguredObject>, Collection<ConfiguredObjectAttribute<?,?>>> _allAttributes =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Collection<ConfiguredObjectAttribute<?, ?>>>());

    private static final Map<Class<? extends ConfiguredObject>, Collection<ConfiguredObjectStatistic<?,?>>> _allStatistics =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Collection<ConfiguredObjectStatistic<?, ?>>>());

    private static final Map<Class<? extends ConfiguredObject>, Map<String, ConfiguredObjectAttribute<?,?>>> _allAttributeTypes =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Map<String, ConfiguredObjectAttribute<?, ?>>>());

    private static final Map<Class<? extends ConfiguredObject>, Map<String, AutomatedField>> _allAutomatedFields =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Map<String, AutomatedField>>());

    private static final Map<String, String> _defaultContext =
            Collections.synchronizedMap(new HashMap<String, String>());

    private static final Map<Class<? extends ConfiguredObject>,Set<Class<? extends ConfiguredObject>>> _knownTypes =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Set<Class<? extends ConfiguredObject>>>());

    private static final Map<Class<? extends ConfiguredObject>, Collection<ConfiguredObjectAttribute<?,?>>> _typeSpecificAttributes =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Collection<ConfiguredObjectAttribute<?, ?>>>());

    private static final Map<Class<? extends ConfiguredObject>, Map<State, Map<State, Method>>> _stateChangeMethods =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Map<State, Map<State, Method>>>());

    static
    {
        QpidServiceLoader<ConfiguredObjectRegistration> loader = new QpidServiceLoader<>();

        Set<Class<? extends ConfiguredObject>> categories = new HashSet<>();
        Set<Class<? extends ConfiguredObject>> types = new HashSet<>();

        for (ConfiguredObjectRegistration registration : loader.instancesOf(ConfiguredObjectRegistration.class))
        {
            for (Class<? extends ConfiguredObject> configuredObjectClass : registration.getConfiguredObjectClasses())
            {
                try
                {
                    process(configuredObjectClass);
                    ManagedObject annotation = configuredObjectClass.getAnnotation(ManagedObject.class);
                    if (annotation.category())
                    {
                        categories.add(configuredObjectClass);
                    }
                    else
                    {
                        Class<? extends ConfiguredObject> category = getCategory(configuredObjectClass);
                        if(category != null)
                        {
                            categories.add(category);
                        }
                    }
                    if (!"".equals(annotation.type()))
                    {
                        types.add(configuredObjectClass);
                    }
                }
                catch(NoClassDefFoundError ncdfe)
                {
                    LOGGER.warn("A class definition could not be found while processing the model for '" + configuredObjectClass.getName() + "': " + ncdfe.getMessage());
                }
            }
        }
        for (Class<? extends ConfiguredObject> categoryClass : categories)
        {
            _knownTypes.put(categoryClass, new HashSet<Class<? extends ConfiguredObject>>());
        }

        for (Class<? extends ConfiguredObject> typeClass : types)
        {
            for (Class<? extends ConfiguredObject> categoryClass : categories)
            {
                if (categoryClass.isAssignableFrom(typeClass))
                {
                    _knownTypes.get(categoryClass).add(typeClass);
                }
            }
        }

        for (Class<? extends ConfiguredObject> categoryClass : categories)
        {
            Set<Class<? extends ConfiguredObject>> typesForCategory = _knownTypes.get(categoryClass);
            if (typesForCategory.isEmpty())
            {
                typesForCategory.add(categoryClass);
                _typeSpecificAttributes.put(categoryClass, Collections.<ConfiguredObjectAttribute<?, ?>>emptySet());
            }
            else
            {
                Set<String> commonAttributes = new HashSet<>();
                for(ConfiguredObjectAttribute<?,?> attribute : _allAttributes.get(categoryClass))
                {
                    commonAttributes.add(attribute.getName());
                }
                for(Class<? extends ConfiguredObject> typeClass : typesForCategory)
                {
                    Set<ConfiguredObjectAttribute<?,?>> attributes = new HashSet<>();
                    for(ConfiguredObjectAttribute<?,?> attr : _allAttributes.get(typeClass))
                    {
                        if(!commonAttributes.contains(attr.getName()))
                        {
                            attributes.add(attr);
                        }
                    }
                    _typeSpecificAttributes.put(typeClass, attributes);
                }

            }
        }
    }

    public static Class<? extends ConfiguredObject> getCategory(final Class<?> clazz)
    {
        ManagedObject annotation = clazz.getAnnotation(ManagedObject.class);
        if(annotation != null && annotation.category())
        {
            return (Class<? extends ConfiguredObject>) clazz;
        }
        for(Class<?> iface : clazz.getInterfaces() )
        {
            Class<? extends ConfiguredObject> cat = getCategory(iface);
            if(cat != null)
            {
                return cat;
            }
        }
        if(clazz.getSuperclass() != null)
        {
            return getCategory(clazz.getSuperclass());
        }
        return null;
    }

    public static Class<? extends ConfiguredObject> getTypeClass(final Class<? extends ConfiguredObject> clazz)
    {
        String typeName = getType(clazz);
        Class<? extends ConfiguredObject> typeClass = null;
        if(typeName != null)
        {
            Class<? extends ConfiguredObject> category = getCategory(clazz);
            Set<Class<? extends ConfiguredObject>> types = _knownTypes.get(category);
            if(types != null)
            {
                for (Class<? extends ConfiguredObject> type : types)
                {
                    ManagedObject annotation = type.getAnnotation(ManagedObject.class);
                    if (typeName.equals(annotation.type()))
                    {
                        typeClass = type;
                        break;
                    }
                }
            }
            if(typeClass == null && typeName.equals(category.getSimpleName()))
            {
                typeClass = category;
            }
        }

        return typeClass;

    }

    public static Collection<Class<? extends ConfiguredObject>> getTypeSpecialisations(Class<? extends ConfiguredObject> clazz)
    {
        Class<? extends ConfiguredObject> categoryClass = getCategory(clazz);
        if(categoryClass == null)
        {
            throw new IllegalArgumentException("Cannot locate ManagedObject information for " + clazz.getName());
        }
        Set<Class<? extends ConfiguredObject>> classes = _knownTypes.get(categoryClass);
        if(classes == null)
        {
            classes = (Set<Class<? extends ConfiguredObject>>) ((Set)Collections.singleton(clazz));
        }
        return Collections.unmodifiableCollection(classes);

    }

    public static Collection<ConfiguredObjectAttribute<?,?>> getTypeSpecificAttributes(Class<? extends ConfiguredObject> clazz)
    {
        Class<? extends ConfiguredObject> typeClass = getTypeClass(clazz);
        if(typeClass == null)
        {
            throw new IllegalArgumentException("Cannot locate ManagedObject information for " + clazz.getName());
        }
        Collection<ConfiguredObjectAttribute<?, ?>> typeAttrs = _typeSpecificAttributes.get(typeClass);
        return Collections.unmodifiableCollection(typeAttrs == null ? Collections.<ConfiguredObjectAttribute<?, ?>>emptySet() : typeAttrs);
    }

    public static String getType(final Class<? extends ConfiguredObject> clazz)
    {
        String type = getActualType(clazz);

        if("".equals(type))
        {
            Class<? extends ConfiguredObject> category = getCategory(clazz);
            if (category == null)
            {
                throw new IllegalArgumentException("No category for " + clazz.getSimpleName());
            }
            ManagedObject annotation = category.getAnnotation(ManagedObject.class);
            if (annotation == null)
            {
                throw new NullPointerException("No definition found for category " + category.getSimpleName());
            }
            if (!"".equals(annotation.defaultType()))
            {
                type = annotation.defaultType();
            }
            else
            {
                type = category.getSimpleName();
            }
        }
        return type;
    }

    private static String getActualType(final Class<? extends ConfiguredObject> clazz)
    {
        ManagedObject annotation = clazz.getAnnotation(ManagedObject.class);
        if(annotation != null)
        {
            if(!"".equals(annotation.type()))
            {
                return annotation.type();
            }
        }

        for(Class<?> iface : clazz.getInterfaces() )
        {
            if(ConfiguredObject.class.isAssignableFrom(iface))
            {
                String type = getActualType((Class<? extends ConfiguredObject>) iface);
                if(!"".equals(type))
                {
                    return type;
                }
            }
        }

        if(clazz.getSuperclass() != null && ConfiguredObject.class.isAssignableFrom(clazz.getSuperclass()))
        {
            String type = getActualType((Class<? extends ConfiguredObject>) clazz.getSuperclass());
            if(!"".equals(type))
            {
                return type;
            }
        }

        return "";
    }

    public static Strings.Resolver getDefaultContextResolver()
    {
        return new Strings.MapResolver(_defaultContext);
    }

    static class AutomatedField
    {
        private final Field _field;
        private final Method _preSettingAction;
        private final Method _postSettingAction;

        private AutomatedField(final Field field, final Method preSettingAction, final Method postSettingAction)
        {
            _field = field;
            _preSettingAction = preSettingAction;
            _postSettingAction = postSettingAction;
        }

        public Field getField()
        {
            return _field;
        }

        public Method getPreSettingAction()
        {
            return _preSettingAction;
        }

        public Method getPostSettingAction()
        {
            return _postSettingAction;
        }
    }




    private static <X extends ConfiguredObject> void process(final Class<X> clazz)
    {
        synchronized (_allAttributes)
        {
            if(_allAttributes.containsKey(clazz))
            {
                return;
            }


            for(Class<?> parent : clazz.getInterfaces())
            {
                if(ConfiguredObject.class.isAssignableFrom(parent))
                {
                    process((Class<? extends ConfiguredObject>) parent);
                }
            }
            final Class<? super X> superclass = clazz.getSuperclass();
            if(superclass != null && ConfiguredObject.class.isAssignableFrom(superclass))
            {
                process((Class<? extends ConfiguredObject>) superclass);
            }

            final SortedSet<ConfiguredObjectAttribute<?, ?>> attributeSet = new TreeSet<>(NAME_COMPARATOR);
            final SortedSet<ConfiguredObjectStatistic<?, ?>> statisticSet = new TreeSet<>(NAME_COMPARATOR);

            _allAttributes.put(clazz, attributeSet);
            _allStatistics.put(clazz, statisticSet);

            for(Class<?> parent : clazz.getInterfaces())
            {
                if(ConfiguredObject.class.isAssignableFrom(parent))
                {
                    initialiseWithParentAttributes(attributeSet,
                                                   statisticSet,
                                                   (Class<? extends ConfiguredObject>) parent);
                }
            }
            if(superclass != null && ConfiguredObject.class.isAssignableFrom(superclass))
            {
                initialiseWithParentAttributes(attributeSet,
                                               statisticSet,
                                               (Class<? extends ConfiguredObject>) superclass);
            }


            for(Method m : clazz.getDeclaredMethods())
            {

                if(m.isAnnotationPresent(ManagedAttribute.class))
                {
                    ManagedAttribute annotation = m.getAnnotation(ManagedAttribute.class);

                    if(!clazz.isInterface() || !ConfiguredObject.class.isAssignableFrom(clazz))
                    {
                        throw new ServerScopedRuntimeException("Can only define ManagedAttributes on interfaces which extend " + ConfiguredObject.class.getSimpleName() + ". " + clazz.getSimpleName() + " does not meet these criteria.");
                    }

                    ConfiguredObjectAttribute<?,?> attribute = new ConfiguredAutomatedAttribute<>(clazz, m, annotation);
                    if(attributeSet.contains(attribute))
                    {
                        attributeSet.remove(attribute);
                    }
                    attributeSet.add(attribute);
                }
                else if(m.isAnnotationPresent(DerivedAttribute.class))
                {
                    DerivedAttribute annotation = m.getAnnotation(DerivedAttribute.class);

                    if(!clazz.isInterface() || !ConfiguredObject.class.isAssignableFrom(clazz))
                    {
                        throw new ServerScopedRuntimeException("Can only define DerivedAttributes on interfaces which extend " + ConfiguredObject.class.getSimpleName() + ". " + clazz.getSimpleName() + " does not meet these criteria.");
                    }

                    ConfiguredObjectAttribute<?,?> attribute = new ConfiguredDerivedAttribute<>(clazz, m, annotation);
                    if(attributeSet.contains(attribute))
                    {
                        attributeSet.remove(attribute);
                    }
                    attributeSet.add(attribute);

                }
                else if(m.isAnnotationPresent(ManagedStatistic.class))
                {
                    ManagedStatistic statAnnotation = m.getAnnotation(ManagedStatistic.class);
                    if(!clazz.isInterface() || !ConfiguredObject.class.isAssignableFrom(clazz))
                    {
                        throw new ServerScopedRuntimeException("Can only define ManagedStatistics on interfaces which extend " + ConfiguredObject.class.getSimpleName() + ". " + clazz.getSimpleName() + " does not meet these criteria.");
                    }
                    ConfiguredObjectStatistic statistic = new ConfiguredObjectStatistic(clazz, m);
                    if(statisticSet.contains(statistic))
                    {
                        statisticSet.remove(statistic);
                    }
                    statisticSet.add(statistic);
                }
            }

            processAttributesTypesAndFields(clazz);

            processDefaultContext(clazz);

            processStateChangeMethods(clazz);
        }
    }

    private static void initialiseWithParentAttributes(final SortedSet<ConfiguredObjectAttribute<?, ?>> attributeSet,
                                                       final SortedSet<ConfiguredObjectStatistic<?, ?>> statisticSet,
                                                       final Class<? extends ConfiguredObject> parent)
    {
        Collection<ConfiguredObjectAttribute<?, ?>> attrs = _allAttributes.get(parent);
        for(ConfiguredObjectAttribute<?,?> attr : attrs)
        {
            if(!attributeSet.contains(attr))
            {
                attributeSet.add(attr);
            }
        }
        Collection<ConfiguredObjectStatistic<?, ?>> stats = _allStatistics.get(parent);
        for(ConfiguredObjectStatistic<?,?> stat : stats)
        {
            if(!statisticSet.contains(stat))
            {
                statisticSet.add(stat);
            }
        }
    }

    private static <X extends ConfiguredObject> void processAttributesTypesAndFields(final Class<X> clazz)
    {
        Map<String,ConfiguredObjectAttribute<?,?>> attrMap = new HashMap<String, ConfiguredObjectAttribute<?, ?>>();
        Map<String,AutomatedField> fieldMap = new HashMap<String, AutomatedField>();


        Collection<ConfiguredObjectAttribute<?, ?>> attrCol = _allAttributes.get(clazz);
        for(ConfiguredObjectAttribute<?,?> attr : attrCol)
        {
            attrMap.put(attr.getName(), attr);
            if(attr.isAutomated())
            {
                fieldMap.put(attr.getName(), findField(attr, clazz));
            }

        }
        _allAttributeTypes.put(clazz, attrMap);
        _allAutomatedFields.put(clazz, fieldMap);
    }

    private static <X extends ConfiguredObject> void processDefaultContext(final Class<X> clazz)
    {
        for(Field field : clazz.getDeclaredFields())
        {
            if(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.isAnnotationPresent(ManagedContextDefault.class))
            {
                try
                {
                    String name = field.getAnnotation(ManagedContextDefault.class).name();
                    Object value = field.get(null);
                    if(!_defaultContext.containsKey(name))
                    {
                        _defaultContext.put(name,String.valueOf(value));
                    }
                    else
                    {
                        throw new IllegalArgumentException("Multiple definitions of the default context variable ${"+name+"}");
                    }
                }
                catch (IllegalAccessException e)
                {
                    throw new ServerScopedRuntimeException("Unexpected illegal access exception (only inspecting public static fields)", e);
                }
            }
        }
    }

    private static void processStateChangeMethods(Class<? extends ConfiguredObject> clazz)
    {
        Map<State, Map<State, Method>> map = new HashMap<>();

        _stateChangeMethods.put(clazz, map);

        addStateTransitions(clazz, map);
        for(Class<?> parent : clazz.getInterfaces())
        {
            if(ConfiguredObject.class.isAssignableFrom(parent))
            {
                inheritTransitions((Class<? extends ConfiguredObject>) parent, map);
            }
        }

        Class<?> superclass = clazz.getSuperclass();

        if(superclass != null && ConfiguredObject.class.isAssignableFrom(superclass))
        {
            inheritTransitions((Class<? extends ConfiguredObject>) superclass, map);
        }
    }

    private static void inheritTransitions(final Class<? extends ConfiguredObject> parent,
                                           final Map<State, Map<State, Method>> map)
    {
        Map<State, Map<State, Method>> parentMap = _stateChangeMethods.get(parent);
        for(Map.Entry<State, Map<State,Method>> parentEntry : parentMap.entrySet())
        {
            if(map.containsKey(parentEntry.getKey()))
            {
                Map<State, Method> methodMap = map.get(parentEntry.getKey());
                for(Map.Entry<State,Method> methodEntry : parentEntry.getValue().entrySet())
                {
                    if(!methodMap.containsKey(methodEntry.getKey()))
                    {
                        methodMap.put(methodEntry.getKey(), methodEntry.getValue());
                    }
                }
            }
            else
            {
                map.put(parentEntry.getKey(), new HashMap<State, Method>(parentEntry.getValue()));
            }
        }
    }

    private static void addStateTransitions(final Class<? extends ConfiguredObject> clazz,
                                            final Map<State, Map<State, Method>> map)
    {
        for(Method m : clazz.getDeclaredMethods())
        {
            if(m.isAnnotationPresent(StateTransition.class))
            {
                if(m.getParameterTypes().length == 0)
                {
                    m.setAccessible(true);
                    StateTransition annotation = m.getAnnotation(StateTransition.class);

                    for(State state : annotation.currentState())
                    {
                        addStateTransition(state, annotation.desiredState(), m, map);
                    }

                }
                else
                {
                    throw new ServerScopedRuntimeException("A state transition method must have no arguments. Method " + m.getName() + " on " + clazz.getName() + " does not meet this criteria.");
                }
            }
        }
    }

    private static void addStateTransition(final State fromState,
                                           final State toState,
                                           final Method method,
                                           final Map<State, Map<State, Method>> map)
    {
        if(map.containsKey(fromState))
        {
            Map<State,Method> toMap = map.get(fromState);
            if(!toMap.containsKey(toState))
            {
                toMap.put(toState,method);
            }
        }
        else
        {
            HashMap<State,Method> toMap = new HashMap<>();
            toMap.put(toState,method);
            map.put(fromState, toMap);
        }
    }

    private static AutomatedField findField(final ConfiguredObjectAttribute<?, ?> attr, Class<?> objClass)
    {
        Class<?> clazz = objClass;
        while(clazz != null)
        {
            for(Field field : clazz.getDeclaredFields())
            {
                if(field.isAnnotationPresent(ManagedAttributeField.class) && field.getName().equals("_" + attr.getName().replace('.','_')))
                {
                    try
                    {
                        ManagedAttributeField annotation = field.getAnnotation(ManagedAttributeField.class);
                        field.setAccessible(true);
                        Method beforeSet;
                        if (!"".equals(annotation.beforeSet()))
                        {
                            beforeSet = clazz.getDeclaredMethod(annotation.beforeSet());
                            beforeSet.setAccessible(true);
                        }
                        else
                        {
                            beforeSet = null;
                        }
                        Method afterSet;
                        if (!"".equals(annotation.afterSet()))
                        {
                            afterSet = clazz.getDeclaredMethod(annotation.afterSet());
                            afterSet.setAccessible(true);
                        }
                        else
                        {
                            afterSet = null;
                        }
                        return new AutomatedField(field, beforeSet, afterSet);
                    }
                    catch (NoSuchMethodException e)
                    {
                        throw new ServerScopedRuntimeException("Cannot find method referenced by annotation for pre/post setting action", e);
                    }

                }
            }
            clazz = clazz.getSuperclass();
        }
        if(objClass.isInterface() || Modifier.isAbstract(objClass.getModifiers()))
        {
            return null;
        }
        throw new ServerScopedRuntimeException("Unable to find field definition for automated field " + attr.getName() + " in class " + objClass.getName());
    }

    public static <X extends ConfiguredObject> Collection<String> getAttributeNames(Class<X> clazz)
    {
        final Collection<ConfiguredObjectAttribute<? super X, ?>> attrs = getAttributes(clazz);

        return new AbstractCollection<String>()
        {
            @Override
            public Iterator<String> iterator()
            {
                final Iterator<ConfiguredObjectAttribute<? super X, ?>> underlyingIterator = attrs.iterator();
                return new Iterator<String>()
                {
                    @Override
                    public boolean hasNext()
                    {
                        return underlyingIterator.hasNext();
                    }

                    @Override
                    public String next()
                    {
                        return underlyingIterator.next().getName();
                    }

                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size()
            {
                return attrs.size();
            }
        };

    }

    protected static <X extends ConfiguredObject> Collection<ConfiguredObjectAttribute<? super X, ?>> getAttributes(final Class<X> clazz)
    {
        if(!_allAttributes.containsKey(clazz))
        {
            process(clazz);
        }
        final Collection<ConfiguredObjectAttribute<? super X, ?>> attributes = (Collection) _allAttributes.get(clazz);
        return attributes;
    }


    protected static Collection<ConfiguredObjectStatistic> getStatistics(final Class<? extends ConfiguredObject> clazz)
    {
        if(!_allAttributes.containsKey(clazz))
        {
            process(clazz);
        }
        final Collection<ConfiguredObjectStatistic> statistics = (Collection) _allStatistics.get(clazz);
        return statistics;
    }


    public static Map<String, ConfiguredObjectAttribute<?, ?>> getAttributeTypes(final Class<? extends ConfiguredObject> clazz)
    {
        if(!_allAttributes.containsKey(clazz))
        {
            process(clazz);
        }
        return _allAttributeTypes.get(clazz);
    }

    static Map<String, AutomatedField> getAutomatedFields(Class<? extends ConfiguredObject> clazz)
    {
        if(!_allAttributes.containsKey(clazz))
        {
            process(clazz);
        }
        return _allAutomatedFields.get(clazz);
    }

    static Map<State, Map<State, Method>> getStateChangeMethods(final Class<? extends ConfiguredObject> objectClass)
    {
        if(!_allAttributes.containsKey(objectClass))
        {
            process(objectClass);
        }
        Map<State, Map<State, Method>> map = _stateChangeMethods.get(objectClass);

        return map != null ? Collections.unmodifiableMap(map) : Collections.<State, Map<State, Method>>emptyMap();
    }



}
