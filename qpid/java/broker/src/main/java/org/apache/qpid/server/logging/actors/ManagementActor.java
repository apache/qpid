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
package org.apache.qpid.server.logging.actors;

import org.apache.qpid.server.logging.RootMessageLogger;

import java.text.MessageFormat;
import java.security.Principal;

public class ManagementActor extends AbstractActor
{

    /**
     * LOG FORMAT for the ManagementActor,
     * Uses a MessageFormat call to insert the requried values according to
     * these indicies:
     *
     * 0 - Connection ID
     * 1 - User ID
     * 2 - IP
     */
    public static final String MANAGEMENT_FORMAT = "mng:{0}({1}@{2})";

    /**
     * //todo Correct interface to provide connection details
     * @param user
     * @param rootLogger The RootLogger to use for this Actor
     */
    public ManagementActor(Principal user, RootMessageLogger rootLogger)
    {
        super(rootLogger);

        _logString = "["+ MessageFormat.format(MANAGEMENT_FORMAT,
                                          "<MNG:ConnectionID>",
                                          user.getName(),
                                          "<MNG:RemoteAddress>")
                     + "] ";
    }
}
