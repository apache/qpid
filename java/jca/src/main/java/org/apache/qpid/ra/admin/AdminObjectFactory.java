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
package org.apache.qpid.ra.admin;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminObjectFactory implements ObjectFactory
{
    private static final Logger _log = LoggerFactory.getLogger(AdminObjectFactory.class);

    @Override
    public Object getObjectInstance(Object object, Name name, Context context, Hashtable<?, ?> env) throws Exception
    {

        Object instance = null;

        if (object instanceof Reference)
        {
            Reference ref = (Reference) object;
            String bindingURLString;

            if (ref.getClassName().equals(QpidQueueImpl.class.getName()))
            {
                RefAddr addr = ref.get(QpidQueueImpl.class.getName());
                bindingURLString = (String) addr.getContent();

                if (addr != null)
                {
                    return new QpidQueueImpl(bindingURLString);
                }

            }

            if (ref.getClassName().equals(QpidTopicImpl.class.getName()))
            {
                RefAddr addr = ref.get(QpidTopicImpl.class.getName());
                bindingURLString = (String) addr.getContent();

                if (addr != null)
                {
                    return new QpidTopicImpl(bindingURLString);
                }
            }
        }
        return instance;
    }
}
