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
package org.apache.qpid.messaging.util;


/**
 * Token
 *
 */

public class Token
{

    public static class Type
    {

        private String name;
        private String pattern;

        Type(String name, String pattern)
        {
            this.name = name;
            this.pattern = pattern;
        }

        public String getName()
        {
            return name;
        }

        public String getPattern()
        {
            return pattern;
        }

        public String toString()
        {
            return name;
        }

    }

    private Type type;
    private String value;
    private String input;
    private int position;

    Token(Type type, String value, String input, int position)
    {
        this.type = type;
        this.value = value;
        this.input = input;
        this.position = position;
    }

    public Type getType()
    {
        return type;
    }

    public String getValue()
    {
        return value;
    }

    public int getPosition()
    {
        return position;
    }

    public LineInfo getLineInfo()
    {
        return LineInfo.get(input, position);
    }

    public String toString()
    {
        if (value == null)
        {
            return type.toString();
        }
        else
        {
            return String.format("%s(%s)", type, value);
        }
    }

}
