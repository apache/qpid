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
package org.apache.qpid.management.messaging;

import org.apache.qpid.client.AMQDestination;

public class ManagementDestination extends AMQDestination
{
    public ManagementDestination()
    {
        super("amq.system", "system", "amq.console");
    }

    public boolean isNameRequired()
    {
        return false;
    }

    public String getEncodedName()
    {
        return null;
    }

    public String getRoutingKey()
    {
        return getDestinationName();
    }    
}
