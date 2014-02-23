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

import java.lang.reflect.Method;
import java.util.*;

public final class Attribute<C extends ConfiguredObject, T>
{
    private static Map<Class<? extends ConfiguredObject>, Collection<Attribute<?,?>>> _allAttributes =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Collection<Attribute<?, ?>>>());

    private final String _name;
    private final Class<T> _type;
    private final Converter<T> _converter;
    private final Method _getter;

    private Attribute(Class<C> clazz, String name, Class<T> type, final Method getter)
    {
        _name = name;
        _type = type;
        _converter = getConverter(type);
        _getter = getter;
        addToAttributesSet(clazz, this);
    }

    private static <X> Converter<X> getConverter(final Class<X> type)
    {
        if(type == String.class)
        {
            return (Converter<X>) STRING_CONVERTER;
        }
        else if(type == Integer.class)
        {
            return (Converter<X>) INT_CONVERTER;
        }
        else if(type == Long.class)
        {
            return (Converter<X>) LONG_CONVERTER;
        }
        else if(type == Boolean.class)
        {
            return (Converter<X>) BOOLEAN_CONVERTER;
        }
        else if(type == UUID.class)
        {
            return (Converter<X>) UUID_CONVERTER;
        }
        else if(Enum.class.isAssignableFrom(type))
        {
            return (Converter<X>) new EnumConverter((Class<? extends Enum>)type);
        }
        else if(List.class.isAssignableFrom(type))
        {
            return (Converter<X>) LIST_CONVERTER;
        }
        else if(Map.class.isAssignableFrom(type))
        {
            return (Converter<X>) MAP_CONVERTER;
        }
        else if(Collection.class.isAssignableFrom(type))
        {
            return (Converter<X>) COLLECTION_CONVERTER;
        }
        else if(ConfiguredObject.class.isAssignableFrom(type))
        {
            return (Converter<X>) new ConfiguredObjectConverter(type);
        }
        throw new IllegalArgumentException("Cannot create attributes of type " + type.getName());
    }

    private static void addToAttributesSet(final Class<? extends ConfiguredObject> clazz, final Attribute<?, ?> attribute)
    {
        synchronized (_allAttributes)
        {
            Collection<Attribute<?,?>> classAttributes = _allAttributes.get(clazz);
            if(classAttributes == null)
            {
                classAttributes = new ArrayList<Attribute<?, ?>>();
                for(Map.Entry<Class<? extends ConfiguredObject>, Collection<Attribute<?,?>>> entry : _allAttributes.entrySet())
                {
                    if(entry.getKey().isAssignableFrom(clazz))
                    {
                        classAttributes.addAll(entry.getValue());
                    }
                }
                _allAttributes.put(clazz, classAttributes);

            }
            for(Map.Entry<Class<? extends ConfiguredObject>, Collection<Attribute<?,?>>> entry : _allAttributes.entrySet())
            {
                if(clazz.isAssignableFrom(entry.getKey()))
                {
                    entry.getValue().add(attribute);
                }
            }

        }
    }

    public String getName()
    {
        return _name;
    }

    public Class<T> getType()
    {
        return _type;
    }
    
    public T getValue(C configuredObject)
    {
        Object o = configuredObject.getAttribute(_name);
        return _converter.convert(o);
    }


    public T getValue(Map<String, Object> attributeMap)
    {
        Object o = attributeMap.get(_name);
        return _converter.convert(o);
    }


    @Override
    public String toString()
    {
        return _name;
    }

    private static interface Converter<T>
    {
        T convert(Object o);
    }

    private static final Converter<String> STRING_CONVERTER = new Converter<String>()
        {
            @Override
            public String convert(final Object o)
            {
                return o == null ? null : o.toString();
            }
        };

    private static final Converter<UUID> UUID_CONVERTER = new Converter<UUID>()
    {
        @Override
        public UUID convert(final Object o)
        {
            if(o instanceof UUID)
            {
                return (UUID)o;
            }
            else if(o instanceof String)
            {
                return UUID.fromString((String)o);
            }
            else if(o == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a UUID");
            }
        }
    };

    private static final Converter<Long> LONG_CONVERTER = new Converter<Long>()
    {

        @Override
        public Long convert(final Object o)
        {
            if(o instanceof Long)
            {
                return (Long)o;
            }
            else if(o instanceof Number)
            {
                return ((Number)o).longValue();
            }
            else if(o instanceof String)
            {
                return Long.valueOf((String)o);
            }
            else if(o == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a Long");
            }
        }
    };

    private static final Converter<Integer> INT_CONVERTER = new Converter<Integer>()
    {

        @Override
        public Integer convert(final Object o)
        {
            if(o instanceof Integer)
            {
                return (Integer)o;
            }
            else if(o instanceof Number)
            {
                return ((Number)o).intValue();
            }
            else if(o instanceof String)
            {
                return Integer.valueOf((String)o);
            }
            else if(o == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to an Integer");
            }
        }
    };

    private static final Converter<Boolean> BOOLEAN_CONVERTER = new Converter<Boolean>()
    {

        @Override
        public Boolean convert(final Object o)
        {
            if(o instanceof Boolean)
            {
                return (Boolean)o;
            }
            else if(o instanceof String)
            {
                return Boolean.valueOf((String)o);
            }
            else if(o == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a Boolean");
            }
        }
    };

    private static final Converter<List> LIST_CONVERTER = new Converter<List>()
    {
        @Override
        public List convert(final Object o)
        {
            if(o instanceof List)
            {
                return (List)o;
            }
            else if(o == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a List");
            }
        }
    };

    private static final Converter<Collection> COLLECTION_CONVERTER = new Converter<Collection>()
    {
        @Override
        public Collection convert(final Object o)
        {
            if(o instanceof Collection)
            {
                return (Collection)o;
            }
            else if(o == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a List");
            }
        }
    };

    private static final Converter<Map> MAP_CONVERTER = new Converter<Map>()
    {
        @Override
        public Map convert(final Object o)
        {
            if(o instanceof Map)
            {
                return (Map)o;
            }
            else if(o == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a Map");
            }
        }
    };

    private static final class EnumConverter<X extends Enum<X>> implements Converter<X>
    {
        private final Class<X> _klazz;

        private EnumConverter(final Class<X> klazz)
        {
            _klazz = klazz;
        }

        @Override
        public X convert(final Object o)
        {
            if(o == null)
            {
                return null;
            }
            else if(_klazz.isInstance(o))
            {
                return (X) o;
            }
            else if(o instanceof String)
            {
                return Enum.valueOf(_klazz,(String)o);
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a " + _klazz.getName());
            }
        }
    }

    private static final class ConfiguredObjectConverter<X extends ConfiguredObject<X>> implements Converter<X>
    {
        private final Class<X> _klazz;

        private ConfiguredObjectConverter(final Class<X> klazz)
        {
            _klazz = klazz;
        }

        @Override
        public X convert(final Object o)
        {
            if(o == null)
            {
                return null;
            }
            else if(_klazz.isInstance(o))
            {
                return (X) o;
            }
            // TODO - traverse tree based on UUID
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + o.getClass() + " to a " + _klazz.getName());
            }
        }
    }


    public static <X extends ConfiguredObject> Collection<String> getAttributeNames(Class<X> clazz)
    {
        final Collection<Attribute<? super X, ?>> attrs = getAttributes(clazz);

        return new AbstractCollection<String>()
        {
            @Override
            public Iterator<String> iterator()
            {
                final Iterator<Attribute<? super X, ?>> underlyingIterator = attrs.iterator();
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

    protected static <X extends ConfiguredObject> Collection<Attribute<? super X, ?>> getAttributes(final Class<X> clazz)
    {
        if(!_allAttributes.containsKey(clazz))
        {
            processAttributes(clazz);
        }
        final Collection<Attribute<? super X, ?>> attributes = (Collection) _allAttributes.get(clazz);
        return attributes;
    }

    private static <X extends ConfiguredObject> void processAttributes(final Class<X> clazz)
    {
        if(_allAttributes.containsKey(clazz))
        {
            return;
        }


        for(Class<?> parent : clazz.getInterfaces())
        {
            if(ConfiguredObject.class.isAssignableFrom(parent))
            {
                processAttributes((Class<? extends ConfiguredObject>)parent);
            }
        }
        final Class<? super X> superclass = clazz.getSuperclass();
        if(superclass != null && ConfiguredObject.class.isAssignableFrom(superclass))
        {
            processAttributes((Class<? extends ConfiguredObject>) superclass);
        }

        final ArrayList<Attribute<?, ?>> attributeList = new ArrayList<Attribute<?, ?>>();
        _allAttributes.put(clazz, attributeList);

        for(Class<?> parent : clazz.getInterfaces())
        {
            if(ConfiguredObject.class.isAssignableFrom(parent))
            {
                Collection<Attribute<?, ?>> attrs = _allAttributes.get(parent);
                for(Attribute<?,?> attr : attrs)
                {
                    if(!attributeList.contains(attr))
                    {
                        attributeList.add(attr);
                    }
                }
            }
        }
        if(superclass != null && ConfiguredObject.class.isAssignableFrom(superclass))
        {
            Collection<Attribute<?, ?>> attrs = _allAttributes.get(superclass);
            for(Attribute<?,?> attr : attrs)
            {
                if(!attributeList.contains(attr))
                {
                    attributeList.add(attr);
                }
            }

        }


        for(Method m : clazz.getDeclaredMethods())
        {
            ManagedAttribute annotation = m.getAnnotation(ManagedAttribute.class);
            if(annotation != null)
            {
                if(m.getParameterTypes().length != 0)
                {
                    throw new IllegalArgumentException("ManagedAttribute annotation should only be added to no-arg getters");
                }
                Class<?> type = m.getReturnType();
                if(type.isPrimitive())
                {
                    if(type == Boolean.TYPE)
                    {
                        type = Boolean.class;
                    }
                    else if(type == Byte.TYPE)
                    {
                        type = Byte.class;
                    }
                    else if(type == Short.TYPE)
                    {
                        type = Short.class;
                    }
                    else if(type == Integer.TYPE)
                    {
                        type = Integer.class;
                    }
                    else if(type == Long.TYPE)
                    {
                        type = Long.class;
                    }
                    else if(type == Float.TYPE)
                    {
                        type = Float.class;
                    }
                    else if(type == Double.TYPE)
                    {
                        type = Double.class;
                    }
                    else if(type == Character.TYPE)
                    {
                        type = Character.class;
                    }
                }
                String methodName = m.getName();
                String baseName;

                if(type == Boolean.class )
                {
                    if((methodName.startsWith("get") || methodName.startsWith("has")) && methodName.length() >= 4)
                    {
                        baseName = methodName.substring(3);
                    }
                    else if(methodName.startsWith("is") && methodName.length() >= 3)
                    {
                        baseName = methodName.substring(2);
                    }
                    else
                    {
                        throw new IllegalArgumentException("Method name " + methodName + " does not conform to the required pattern for ManagedAttributes");
                    }
                }
                else
                {
                    if(methodName.startsWith("get") && methodName.length() >= 4)
                    {
                        baseName = methodName.substring(3);
                    }
                    else
                    {
                        throw new IllegalArgumentException("Method name " + methodName + " does not conform to the required pattern for ManagedAttributes");
                    }
                }

                String name = baseName.length() == 1 ? baseName.toLowerCase() : baseName.substring(0,1).toLowerCase() + baseName.substring(1);
                name = name.replace('_','.');
                Attribute<X,?> newAttr = new Attribute(clazz,name,type,m);

            }
        }
    }

    public static void main(String[] args)
    {
        System.err.println(Attribute.getAttributeNames(KeyStore.class));
        System.err.println(Attribute.getAttributeNames(Binding.class));
        System.err.println(Attribute.getAttributeNames(Exchange.class));
        System.err.println(Attribute.getAttributeNames(Broker.class));

    }


}
