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

package org.apache.qpid.server.configuration;

public interface ConfigProperty<T extends ConfigObjectType<T,C>, C extends ConfiguredObject<T,C>,  S>
{
    public String getName();

    public S getValue(C object);

    public void setValue(C object, S value);

    public void clearValue(C object);

    public abstract static class ReadWriteConfigProperty<T extends ConfigObjectType<T,C>, C extends ConfiguredObject<T,C>,S> implements ConfigProperty<T, C, S>
    {
        private final String _name;

        protected ReadWriteConfigProperty(String name)
        {
            _name = name;
        }

        public final String getName()
        {
            return _name;
        }
    }

    public abstract static class ReadOnlyConfigProperty<T extends ConfigObjectType<T,C>, C extends ConfiguredObject<T,C>, S> extends ReadWriteConfigProperty<T, C, S>
    {
        protected ReadOnlyConfigProperty(String name)
        {
            super(name);
        }

        public final void setValue(C object, S value)
        {
            throw new UnsupportedOperationException("Cannot set value '"+getName()+"' as this property is read-only");
        }

        public final void clearValue(C object)
        {
            throw new UnsupportedOperationException("Cannot set value '"+getName()+"' as this property is read-only");
        }
    }
}
