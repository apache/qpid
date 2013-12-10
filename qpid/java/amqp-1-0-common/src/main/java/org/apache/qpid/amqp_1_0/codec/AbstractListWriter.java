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
package org.apache.qpid.amqp_1_0.codec;

public abstract class AbstractListWriter<V> extends CompoundWriter<V>
{
    public AbstractListWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected byte getFourOctetEncodingCode()
    {
        return (byte)0xd0;
    }

    @Override
    protected byte getSingleOctetEncodingCode()
    {
        return (byte)0xc0;
    }
}
