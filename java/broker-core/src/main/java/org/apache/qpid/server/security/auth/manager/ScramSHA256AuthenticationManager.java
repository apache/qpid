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
package org.apache.qpid.server.security.auth.manager;

import java.nio.charset.Charset;
import java.util.Map;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;

@ManagedObject( category = false, type = "SCRAM-SHA-256" )
public class ScramSHA256AuthenticationManager
        extends AbstractScramAuthenticationManager<ScramSHA256AuthenticationManager>
{
    public static final String PROVIDER_TYPE = "SCRAM-SHA-256";
    private static final String HMAC_NAME = "HmacSHA256";

    static final Charset ASCII = Charset.forName("ASCII");
    private static final String MECHANISM = "SCRAM-SHA-256";
    private static final String DIGEST_NAME = "SHA-256";


    @ManagedObjectFactoryConstructor
    protected ScramSHA256AuthenticationManager(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }

    @Override
    protected String getMechanismName()
    {
        return MECHANISM;
    }

    @Override
    protected String getDigestName()
    {
        return DIGEST_NAME;
    }

    @Override
    protected String getHmacName()
    {
        return HMAC_NAME;
    }

}
