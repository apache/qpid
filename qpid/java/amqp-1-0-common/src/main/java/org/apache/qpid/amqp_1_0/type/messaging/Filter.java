
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


package org.apache.qpid.amqp_1_0.type.messaging;



import org.apache.qpid.amqp_1_0.type.*;

public class Filter
  {


    private Symbol _type;

    private Predicate _predicate;

    public Symbol getType()
    {
        return _type;
    }

    public void setType(Symbol type)
    {
        _type = type;
    }

    public Predicate getPredicate()
    {
        return _predicate;
    }

    public void setPredicate(Predicate predicate)
    {
        _predicate = predicate;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("Filter{");
        final int origLength = builder.length();

        if(_type != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("type=").append(_type);
        }

        if(_predicate != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("predicate=").append(_predicate);
        }

        builder.append('}');
        return builder.toString();
    }


  }
