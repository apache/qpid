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
package org.apache.qpid.server.security.auth.sasl;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslServerFactory;
import java.util.Map;

public interface AuthenticationProviderInitialiser
{
    /**
     * @return the mechanism's name. This will be used in the list of mechanism's advertised to the
     * client.
     */
    String getMechanismName();

    /**
     * @return the callback handler that should be used to process authentication requests for this mechanism. This will
     * be called after initialise and will be stored by the authentication manager. The callback handler <b>must</b> be
     * fully threadsafe.
     */
    CallbackHandler getCallbackHandler();

    /**
     * Get the properties that must be passed in to the Sasl.createSaslServer method.
     * @return the properties, which may be null
     */
    Map<String, ?> getProperties();

    /**
     * Get the class that is the server factory. This is used for the JCA registration.
     * @return null if no JCA registration is required, otherwise return the class
     * that will be used in JCA registration
     */
    Class<? extends SaslServerFactory> getServerFactoryClassForJCARegistration();
}
