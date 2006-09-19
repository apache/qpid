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
import java.security.Provider;
import java.security.Security;
import java.util.Map;

public final class JCAProvider extends Provider
{
    public JCAProvider(Map<String, Class<? extends SaslServerFactory>> providerMap)
    {
        super("AMQSASLProvider", 1.0, "A JCA provider that registers all " +
              "AMQ SASL providers that want to be registered");
        register(providerMap);
        Security.addProvider(this);
    }

    private void register(Map<String, Class<? extends SaslServerFactory>> providerMap)
    {
        for (Map.Entry<String, Class<? extends SaslServerFactory>> me :
             providerMap.entrySet())
        {
            put("SaslServerFactory." + me.getKey(), me.getValue().getName());
        }
    }
}