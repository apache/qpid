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

package org.apache.qpid.qmf;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

public class QMFPackage
{
    private final String _name;
    private final Map<String, QMFClass> _classes = new HashMap<String, QMFClass>();

    public QMFPackage(String name)
    {
        _name = name;
    }

    public QMFPackage(String name, Collection<QMFClass> classes)
    {
        this(name);
        setClasses(classes);
    }

    protected void setClasses(Collection<QMFClass> classes)
    {
        for(QMFClass qmfClass : classes)
        {
            qmfClass.setPackage(this);
            _classes.put(qmfClass.getName(), qmfClass);
        }
    }

    public String getName()
    {
        return _name;
    }

    public Collection<QMFClass> getClasses()
    {
        return _classes.values();
    }

    public QMFClass getQMFClass(String className)
    {
        return _classes.get(className);
    }
}
