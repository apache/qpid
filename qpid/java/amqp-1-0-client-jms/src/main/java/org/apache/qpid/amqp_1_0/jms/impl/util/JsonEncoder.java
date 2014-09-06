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
package org.apache.qpid.amqp_1_0.jms.impl.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class JsonEncoder
{
    public void encode(Object object, Writer writer) throws IOException
    {
        if(object == null)
        {
            writer.append("null");
        }
        else if(object instanceof Boolean)
        {
            writer.append(((Boolean)object)? "true" : "false");
        }
        else if(object instanceof Number)
        {
            writer.append(object.toString());
        }
        else if(object instanceof Object[])
        {
            writer.append("[ ");
            boolean first = true;
            for(Object element : ((Object[])object))
            {
                if(first)
                {
                    first = false;
                }
                else
                {
                    writer.append(',');
                }
                encode(element, writer);
                writer.append(" ]");
            }
        }
        else if(object instanceof Iterable)
        {
            writer.append("[ ");
            boolean first = true;
            for(Object element : ((Iterable)object))
            {
                if(first)
                {
                    first = false;
                }
                else
                {
                    writer.append(", ");
                }
                encode(element, writer);
            }
            writer.append(" ]");
        }
        else if(object instanceof Map)
        {
            writer.append("{ ");
            boolean first = true;
            for(Map.Entry element : ((Map<?,?>)object).entrySet())
            {
                if(first)
                {
                    first = false;
                }
                else
                {
                    writer.append(", ");
                }
                encode(element.getKey(), writer);
                writer.append(" : ");
                encode(element.getValue(), writer);
            }
            writer.append(" }");
        }
        else if(object instanceof CharSequence)
        {
            CharSequence string = (CharSequence)object;
            writer.append('"');
            for(int i = 0; i < string.length(); i++)
            {
                char c = string.charAt(i);
                if(c == '"' || c=='\\')
                {
                    writer.append('\\');
                }
                writer.append(c);
            }
            writer.append('"');
        }
        else
        {
            throw new IllegalArgumentException("Don't know how to encode class " + object.getClass().getName());
        }
    }

    public String encode(Object object) throws IOException
    {
        StringWriter writer = new StringWriter();
        encode(object, writer);
        return writer.toString();
    }


    public static void main(String[] args) throws Exception
    {
        JsonEncoder encoder = new JsonEncoder();
        String encoded =
                encoder.encode(Collections.singletonMap("hello \\\" world", Arrays.asList(3, 7.2, false, null, 4)));
        Object decoded = new JsonDecoder().decode(new StringReader(encoded));
        System.err.println(encoded);
    }
}
