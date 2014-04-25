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

import java.util.UUID;

public class UnknownConfiguredObjectException extends IllegalArgumentException
{
    private final Class<? extends ConfiguredObject> _category;
    private String _name;
    private UUID _id;

    public UnknownConfiguredObjectException(final Class<? extends ConfiguredObject> category, final String name)
    {
        super("Could not find object of category " + category.getSimpleName() + " with name '" + name + "'");
        _category = category;
        _name = name;
    }

    public UnknownConfiguredObjectException(final Class<? extends ConfiguredObject> category, final UUID id)
    {
        super("Could not find object of category " + category.getSimpleName() + " with id " + id);
        _category = category;
        _id = id;
    }

    public Class<? extends ConfiguredObject> getCategory()
    {
        return _category;
    }

    public String getName()
    {
        return _name;
    }

    public UUID getId()
    {
        return _id;
    }
}
