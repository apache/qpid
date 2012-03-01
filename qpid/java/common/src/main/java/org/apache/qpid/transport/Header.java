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
package org.apache.qpid.transport;

import java.util.List;


/**
 * Header
 *
 * @author Rafael H. Schloming
 */

public class Header
{

    private final DeliveryProperties _deliveryProps;
    private final MessageProperties _messageProps;
    private final List<Struct> _nonStandardProps;

    public Header(DeliveryProperties deliveryProps, MessageProperties messageProps)
    {
        this(deliveryProps, messageProps, null);
    }

    public Header(DeliveryProperties deliveryProps, MessageProperties messageProps, List<Struct> nonStandardProps)
    {
        _deliveryProps = deliveryProps;
        _messageProps = messageProps;
        _nonStandardProps = nonStandardProps;
    }

    public Struct[] getStructs()
    {
        int size = 0;
        if(_deliveryProps != null)
        {
            size++;
        }
        if(_messageProps != null)
        {
            size++;
        }
        if(_nonStandardProps != null)
        {
            size+=_nonStandardProps.size();
        }
        Struct[] structs = new Struct[size];
        int index = 0;
        if(_deliveryProps != null)
        {
            structs[index++] = _deliveryProps;
        }
        if(_messageProps != null)
        {
            structs[index++] = _messageProps;
        }
        if(_nonStandardProps != null)
        {
            for(Struct struct : _nonStandardProps)
            {
                structs[index++] = struct;
            }
        }

        return structs;
    }

    public DeliveryProperties getDeliveryProperties()
    {
        return _deliveryProps;
    }

    public MessageProperties getMessageProperties()
    {
        return _messageProps;
    }

    public List<Struct> getNonStandardProperties()
    {
        return _nonStandardProps;
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.append(" Header(");
        boolean first = true;
        if(_deliveryProps !=null)
        {
            first=false;
            str.append(_deliveryProps);
        }
        if(_messageProps != null)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                str.append(", ");
            }
            str.append(_messageProps);
        }
        if(_nonStandardProps != null)
        {
            for (Struct s : _nonStandardProps)
            {
                if (first)
                {
                    first = false;
                }
                else
                {
                    str.append(", ");
                }
                str.append(s);
            }
        }
        str.append(')');
        return str.toString();
    }

}
