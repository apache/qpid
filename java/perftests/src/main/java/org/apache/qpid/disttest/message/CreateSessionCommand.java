/*
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
package org.apache.qpid.disttest.message;

public class CreateSessionCommand extends Command
{
    private String sessionName;
    private String connectionName;
    private int acknowledgeMode;

    public CreateSessionCommand()
    {
        super(CommandType.CREATE_SESSION);
    }

    public String getSessionName()
    {
        return sessionName;
    }

    public void setSessionName(final String sessionName)
    {
        this.sessionName = sessionName;
    }

    public String getConnectionName()
    {
        return connectionName;
    }

    public void setConnectionName(final String connectionName)
    {
        this.connectionName = connectionName;
    }

    public int getAcknowledgeMode()
    {
        return acknowledgeMode;
    }

    public void setAcknowledgeMode(final int acknowledgeMode)
    {
        this.acknowledgeMode = acknowledgeMode;
    }
}
