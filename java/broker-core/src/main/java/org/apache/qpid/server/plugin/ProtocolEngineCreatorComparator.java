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
package org.apache.qpid.server.plugin;

import java.util.Comparator;

public class ProtocolEngineCreatorComparator implements Comparator<ProtocolEngineCreator>
{
    @Override
    public int compare(ProtocolEngineCreator pec1, ProtocolEngineCreator pec2)
    {
        final AMQPProtocolVersionWrapper v1 = new AMQPProtocolVersionWrapper(pec1.getVersion());
        final AMQPProtocolVersionWrapper v2 = new AMQPProtocolVersionWrapper(pec2.getVersion());

        if (v1.getMajor() != v2.getMajor())
        {
            return v1.getMajor() - v2.getMajor();
        }
        else if (v1.getMinor() != v2.getMinor())
        {
            return v1.getMinor() - v2.getMinor();
        }
        else if (v1.getPatch() != v2.getPatch())
        {
            return v1.getPatch() - v2.getPatch();
        }
        else
        {
            return 0;
        }
    }


}
