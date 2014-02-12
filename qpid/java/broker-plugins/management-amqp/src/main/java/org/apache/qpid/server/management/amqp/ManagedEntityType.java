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
package org.apache.qpid.server.management.amqp;

import java.util.Arrays;

class ManagedEntityType
{
    private final String _name;
    private final ManagedEntityType[] _parents;
    private final String[] _attributes;
    private final String[] _operations;

    ManagedEntityType(final String name,
                      final ManagedEntityType[] parents,
                      final String[] attributes,
                      final String[] operations)
    {
        _name = name;
        _parents = parents;
        _attributes = attributes;
        _operations = operations;
    }

    public String getName()
    {
        return _name;
    }

    public ManagedEntityType[] getParents()
    {
        return _parents;
    }

    public String[] getAttributes()
    {
        return _attributes;
    }

    public String[] getOperations()
    {
        return _operations;
    }

    @Override
    public String toString()
    {
        return "ManagedEntityType{" +
               "name='" + _name + '\'' +
               ", parents=" + Arrays.toString(_parents) +
               ", attributes=" + Arrays.toString(_attributes) +
               ", operations=" + Arrays.toString(_operations) +
               '}';
    }
}
