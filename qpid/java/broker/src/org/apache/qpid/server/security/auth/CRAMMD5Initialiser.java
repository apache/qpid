/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.security.auth;

import javax.security.sasl.SaslServerFactory;

public class CRAMMD5Initialiser extends UsernamePasswordInitialiser
{
    public String getMechanismName()
    {
        return "CRAM-MD5";
    }

    public Class<? extends SaslServerFactory> getServerFactoryClassForJCARegistration()
    {
        // since the CRAM-MD5 provider is registered as part of the JDK, we do not
        // return the factory class here since we do not need to register it ourselves.
        return null;
    }
}
