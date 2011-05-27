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
package org.apache.qpid.client.security.anonymous;

import java.util.Arrays;
import java.util.Map;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;
import javax.security.auth.callback.CallbackHandler;

public class AnonymousSaslClientFactory implements SaslClientFactory
{
    public SaslClient createSaslClient(String[] mechanisms, String authId,
                                       String protocol, String server,
                                       Map props, CallbackHandler cbh) throws SaslException
    {
        if (Arrays.asList(mechanisms).contains("ANONYMOUS")) {
            return new AnonymousSaslClient();
        } else {
            return null;
        }
    }
    public String[] getMechanismNames(Map props)
    {
        if (props == null || props.isEmpty()) {
            return new String[]{"ANONYMOUS"};
        } else {
            return new String[0];
        }
    }
}
