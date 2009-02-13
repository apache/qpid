/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.server.filter;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.Filterable;

//
// Based on like named file from r450141 of the Apache ActiveMQ project <http://www.activemq.org/site/home.html>
//

/**
 * Used to evaluate an XQuery Expression in a JMS selector.
 */
public final class XQueryExpression implements BooleanExpression {
    private final String xpath;

    XQueryExpression(String xpath) {
        super();
        this.xpath = xpath;
    }

    public Object evaluate(Filterable message) throws AMQException {
        return Boolean.FALSE;
    }

    public String toString() {
        return "XQUERY "+ConstantExpression.encodeString(xpath);
    }
    
    /**
     * @param message
     * @return true if the expression evaluates to Boolean.TRUE.
     * @throws AMQException
     */
    public boolean matches(Filterable message) throws AMQException
    {
        Object object = evaluate(message);
        return object!=null && object==Boolean.TRUE;            
    }

}
