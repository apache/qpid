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
package org.apache.qpid;

public class AMQUnresolvedAddressException extends AMQException
{
    String _broker;

    public AMQUnresolvedAddressException(String message, String broker)
    {
        super(message);
        _broker = broker;
    }

    public String toString()
    {
        return super.toString() + " Broker, \"" + _broker +"\"";
    }
}
