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
//
// Based on like named file from r450141 of the Apache ActiveMQ project <http://www.activemq.org/site/home.html>
//

import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;

/**
 * A filter performing a comparison of two objects
 */
public abstract class LogicExpression extends BinaryExpression implements BooleanExpression
{

    public static BooleanExpression createOR(BooleanExpression lvalue, BooleanExpression rvalue)
    {
        return new LogicExpression(lvalue, rvalue)
            {

                public Object evaluate(AMQMessage message) throws AMQException
                {

                    Boolean lv = (Boolean) left.evaluate(message);
                    // Can we do an OR shortcut??
                    if ((lv != null) && lv.booleanValue())
                    {
                        return Boolean.TRUE;
                    }

                    Boolean rv = (Boolean) right.evaluate(message);

                    return (rv == null) ? null : rv;
                }

                public String getExpressionSymbol()
                {
                    return "OR";
                }
            };
    }

    public static BooleanExpression createAND(BooleanExpression lvalue, BooleanExpression rvalue)
    {
        return new LogicExpression(lvalue, rvalue)
            {

                public Object evaluate(AMQMessage message) throws AMQException
                {

                    Boolean lv = (Boolean) left.evaluate(message);

                    // Can we do an AND shortcut??
                    if (lv == null)
                    {
                        return null;
                    }

                    if (!lv.booleanValue())
                    {
                        return Boolean.FALSE;
                    }

                    Boolean rv = (Boolean) right.evaluate(message);

                    return (rv == null) ? null : rv;
                }

                public String getExpressionSymbol()
                {
                    return "AND";
                }
            };
    }

    /**
     * @param left
     * @param right
     */
    public LogicExpression(BooleanExpression left, BooleanExpression right)
    {
        super(left, right);
    }

    public abstract Object evaluate(AMQMessage message) throws AMQException;

    public boolean matches(AMQMessage message) throws AMQException
    {
        Object object = evaluate(message);

        return (object != null) && (object == Boolean.TRUE);
    }

}
