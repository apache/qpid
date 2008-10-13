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
package org.apache.qpid.management.messages;

import org.apache.qpid.management.Protocol;
import org.apache.qpid.management.domain.model.QpidMethod;
import org.apache.qpid.management.domain.model.type.Binary;

public abstract class MethodInvocationRequestMessage extends ManagementMessage
{
    @Override
    char opcode ()
    {
        return Protocol.OPERATION_INVOCATION_REQUEST_OPCODE;
    }
    
    protected abstract String packageName();
    protected abstract String className();
    protected abstract Binary schemaHash();
    protected abstract Binary objectId();
    protected abstract QpidMethod method();
    protected abstract Object[] parameters();

    @Override
    void specificMessageEncoding ()
    {
        objectId().encode(_codec);
        _codec.packStr8(packageName());
        _codec.packStr8(className());
        schemaHash().encode(_codec);
        
        QpidMethod method = method();
       _codec.packStr8(method.getName());
       method.encodeParameters(parameters(), _codec);
    }
}