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

import org.apache.commons.lang.StringUtils;
import org.apache.qpid.server.model.Protocol;

public class AMQPProtocolVersionWrapper
{
    static final char DELIMITER = '_';

    private int _major;
    private int _minor;
    private int _patch;

    public AMQPProtocolVersionWrapper(Protocol amqpProtocol)
    {
        if (!amqpProtocol.isAMQP())
        {
            throw new IllegalArgumentException("Protocol must be of type " + Protocol.ProtocolType.AMQP);
        }

        final String[] parts = StringUtils.split(amqpProtocol.name(), DELIMITER);
        for (int i = 0; i < parts.length; i++)
        {
            switch (i)
            {
                case 1: this._major = Integer.parseInt(parts[i]);
                    break;
                case 2: this._minor = Integer.parseInt(parts[i]);
                    break;
                case 3: this._patch = Integer.parseInt(parts[i]);
                    break;
            }
        }
    }

    public int getMajor()
    {
        return _major;
    }

    public int getMinor()
    {
        return _minor;
    }

    public int getPatch()
    {
        return _patch;
    }

    public Protocol getProtocol()
    {
        return Protocol.valueOf(this.toString());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof AMQPProtocolVersionWrapper))
        {
            return false;
        }

        final AMQPProtocolVersionWrapper number = (AMQPProtocolVersionWrapper) o;

        if (this._major != number._major)
        {
            return false;
        }
        else if (this._minor != number._minor)
        {
            return false;
        }
        else if (this._patch != number._patch)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    @Override
    public int hashCode()
    {
        int result = _major;
        result = 31 * result + _minor;
        result = 31 * result + _patch;
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(Protocol.ProtocolType.AMQP.name()).append(DELIMITER)
                                     .append(_major).append(DELIMITER)
                                     .append(_minor);
        if (_patch != 0)
        {
            sb.append(DELIMITER).append(_patch);
        }
        return sb.toString();
    }
}
