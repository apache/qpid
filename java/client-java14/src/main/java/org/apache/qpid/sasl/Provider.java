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
package org.apache.qpid.sasl;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.log4j.Logger;

/**
 * A SASL provider for Java 1.4. Declares the capabilities of this implementation, which supports PLAIN and CRAM-MD5
 * client implementations only.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Declare PLAIN SASL support.
 * <tr><td> Declare CRAM-MD5 SASL support.
 * </table>
 */
public class Provider extends java.security.Provider
{
    //Logger log = Logger.getLogger(Provider.class);

    private static final String info = "Qpid SASL provider" + "(implements client mechanisms for: PLAIN, CRAM-MD5)";

    public Provider()
    {
        super("QpidSASL", 1.4, info);

        //log.debug("public Provider(): called");

        AccessController.doPrivileged(new PrivilegedAction()
            {
                public Object run()
                {
                    put("SaslClientFactory.PLAIN", "org.apache.qpid.sasl.ClientFactoryImpl");
                    put("SaslClientFactory.CRAM-MD5", "org.apache.qpid.sasl.ClientFactoryImpl");

                    return null;
                }
            });
    }
}
