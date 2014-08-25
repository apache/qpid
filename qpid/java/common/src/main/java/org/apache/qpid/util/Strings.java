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
package org.apache.qpid.util;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Strings
 *
 */

public final class Strings
{
    private Strings()
    {
    }

    private static final byte[] EMPTY = new byte[0];

    private static final ThreadLocal<char[]> charbuf = new ThreadLocal<char[]>()
    {
        public char[] initialValue()
        {
            return new char[4096];
        }
    };

    public static final byte[] toUTF8(String str)
    {
        if (str == null)
        {
            return EMPTY;
        }
        else
        {
            final int size = str.length();
            char[] chars = charbuf.get();
            if (size > chars.length)
            {
                chars = new char[Math.max(size, 2*chars.length)];
                charbuf.set(chars);
            }

            str.getChars(0, size, chars, 0);
            final byte[] bytes = new byte[size];
            for (int i = 0; i < size; i++)
            {
                if (chars[i] > 127)
                {
                    try
                    {
                        return str.getBytes("UTF-8");
                    }
                    catch (UnsupportedEncodingException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                bytes[i] = (byte) chars[i];
            }
            return bytes;
        }
    }

    public static final String fromUTF8(byte[] bytes)
    {
        try
        {
            return new String(bytes, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static final Pattern VAR = Pattern.compile("(?:\\$\\{([^\\}]*)\\})|(?:\\$(\\$))");

    public static Resolver chain(Resolver... resolvers)
    {
        Resolver resolver;
        if(resolvers.length == 0)
        {
            resolver =  NULL_RESOLVER;
        }
        else
        {
            resolver = resolvers[resolvers.length - 1];
            for (int i = resolvers.length - 2; i >= 0; i--)
            {
                resolver = new ChainedResolver(resolvers[i], resolver);
            }
        }
        return resolver;
    }

    public static interface Resolver
    {
        String resolve(String variable, final Resolver resolver);
    }

    private static final Resolver NULL_RESOLVER =
            new Resolver()
            {
                @Override
                public String resolve(final String variable, final Resolver resolver)
                {
                    return null;
                }
            };

    public static class MapResolver implements Resolver
    {

        private final Map<String,String> map;

        public MapResolver(Map<String,String> map)
        {
            this.map = map;
        }

        public String resolve(String variable, final Resolver resolver)
        {
            return map.get(variable);
        }
    }

    public static class PropertiesResolver implements Resolver
    {

        private final Properties properties;

        public PropertiesResolver(Properties properties)
        {
            this.properties = properties;
        }

        public String resolve(String variable, final Resolver resolver)
        {
            return properties.getProperty(variable);
        }
    }

    public static class ChainedResolver implements Resolver
    {
        private final Resolver primary;
        private final Resolver secondary;

        public ChainedResolver(Resolver primary, Resolver secondary)
        {
            this.primary = primary;
            this.secondary = secondary;
        }

        public String resolve(String variable, final Resolver resolver)
        {
            String result = primary.resolve(variable, resolver);
            if (result == null)
            {
                result = secondary.resolve(variable, resolver);
            }
            return result;
        }
    }

    public static final Resolver ENV_VARS_RESOLVER = new Resolver()
        {
            @Override
            public String resolve(final String variable, final Resolver resolver)
            {
                return System.getenv(variable);
            }
        };


    public static final Resolver JAVA_SYS_PROPS_RESOLVER = new Resolver()
    {
        @Override
        public String resolve(final String variable, final Resolver resolver)
        {
            return System.getProperty(variable);
        }
    };


    public static final Resolver SYSTEM_RESOLVER = chain(JAVA_SYS_PROPS_RESOLVER, ENV_VARS_RESOLVER);

    public static final String expand(String input)
    {
        return expand(input, SYSTEM_RESOLVER);
    }

    public static final String expand(String input, Resolver resolver)
    {
        return expand(input, resolver, new Stack<String>(),true);
    }
    public static final String expand(String input, boolean failOnUnresolved, Resolver... resolvers)
    {
        return expand(input, chain(resolvers), new Stack<String>(), failOnUnresolved);
    }

    private static final String expand(String input, Resolver resolver, Stack<String> stack, boolean failOnUnresolved)
    {
        if (input == null)
        {
            throw new IllegalArgumentException("Expansion input cannot be null");
        }
        Matcher m = VAR.matcher(input);
        StringBuffer result = new StringBuffer();
        while (m.find())
        {
            String var = m.group(1);
            if (var == null)
            {
                String esc = m.group(2);
                if ("$".equals(esc))
                {
                    m.appendReplacement(result, Matcher.quoteReplacement("$"));
                }
                else
                {
                    throw new IllegalArgumentException(esc);
                }
            }
            else
            {
                m.appendReplacement(result, Matcher.quoteReplacement(resolve(var, resolver, stack, failOnUnresolved)));
            }
        }
        m.appendTail(result);
        return result.toString();
    }

    private static final String resolve(String var,
                                        Resolver resolver,
                                        Stack<String> stack,
                                        final boolean failOnUnresolved)
    {
        if (stack.contains(var))
        {
            throw new IllegalArgumentException
                (String.format("recursively defined variable: %s stack=%s", var,
                               stack));
        }

        String result = resolver.resolve(var, resolver);
        if (result == null)
        {
            if(failOnUnresolved)
            {
                throw new IllegalArgumentException("no such variable: " + var);
            }
            else
            {
                return "${"+var+"}";
            }
        }

        stack.push(var);
        try
        {
            return expand(result, resolver, stack, failOnUnresolved);
        }
        finally
        {
            stack.pop();
        }
    }

    public static final String join(String sep, Iterable items)
    {
        StringBuilder result = new StringBuilder();

        for (Object o : items)
        {
            if (result.length() > 0)
            {
                result.append(sep);
            }
            result.append(o.toString());
        }

        return result.toString();
    }

    public static final String join(String sep, Object[] items)
    {
        return join(sep, Arrays.asList(items));
    }

    public static String printMap(Map<String,Object> map)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        if (map != null)
        {
            for(Map.Entry<String,Object> entry : map.entrySet())
            {
                sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append(" ");
            }
        }
        sb.append(">");
        return sb.toString();
    }


    public static Resolver createSubstitutionResolver(String prefix, LinkedHashMap<String,String> substitutions)
    {
        return new StringSubstitutionResolver(prefix, substitutions);
    }

    private static class StringSubstitutionResolver implements Resolver
    {

        private final ThreadLocal<Set<String>> _stack = new ThreadLocal<>();

        private final LinkedHashMap<String, String> _substitutions;
        private final String _prefix;

        private StringSubstitutionResolver(String prefix, LinkedHashMap<String, String> substitutions)
        {
            _prefix = prefix;
            _substitutions = substitutions;
        }

        @Override
        public String resolve(final String variable, final Resolver resolver)
        {
            boolean clearStack = false;
            Set<String> currentStack = _stack.get();
            if(currentStack == null)
            {
                currentStack = new HashSet<>();
                _stack.set(currentStack);
                clearStack = true;
            }

            try
            {
                if(currentStack.contains(variable))
                {
                    throw new IllegalArgumentException("The value of attribute " + variable + " is defined recursively");

                }


                if (variable.startsWith(_prefix))
                {
                    currentStack.add(variable);
                    String expanded = resolver.resolve(variable.substring(_prefix.length()), resolver);
                    currentStack.remove(variable);
                    if(expanded != null)
                    {
                        for(Map.Entry<String,String> entry : _substitutions.entrySet())
                        {
                            expanded = expanded.replace(entry.getKey(), entry.getValue());
                        }
                    }
                    return expanded;
                }
                else
                {
                    return null;
                }

            }
            finally
            {

                if(clearStack)
                {
                    _stack.remove();
                }
            }
        }
    }
}
