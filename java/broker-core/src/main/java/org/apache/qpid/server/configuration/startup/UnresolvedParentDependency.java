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
package org.apache.qpid.server.configuration.startup;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedDependency;

import java.util.UUID;

class UnresolvedParentDependency<X extends ConfiguredObject<X>> implements UnresolvedDependency<X>
{
    private final UUID _id;
    private final String _type;
    private final UnresolvedObjectWithParents _unresolvedObject;

    public UnresolvedParentDependency(final UnresolvedObjectWithParents unresolvedObject,
                                      final String type,
                                      final ConfiguredObjectRecord record)
    {
        _type = type;
        _id = record.getId();
        _unresolvedObject = unresolvedObject;
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public String getType()
    {
        return _type;
    }

    @Override
    public void resolve(final X dependency)
    {
        _unresolvedObject.resolvedParent(this, dependency);
    }
}
