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

import org.apache.qpid.server.queue.Filterable;

/**
 * A filter performing a comparison of two objects
 */
public abstract class LogicExpression<E extends Exception> extends BinaryExpression<E> implements BooleanExpression<E>
{

    public static<E extends Exception> BooleanExpression createOR(BooleanExpression<E> lvalue, BooleanExpression<E> rvalue)
    {
        return new OrExpression(lvalue, rvalue);
    }

    public static<E extends Exception> BooleanExpression createAND(BooleanExpression<E> lvalue, BooleanExpression<E> rvalue)
    {
        return new AndExpression(lvalue, rvalue);
    }

    /**
     * @param left
     * @param right
     */
    public LogicExpression(BooleanExpression left, BooleanExpression right)
    {
        super(left, right);
    }

    public abstract Object evaluate(Filterable<E> message) throws E;

    public boolean matches(Filterable<E> message) throws E
    {
        Object object = evaluate(message);

        return (object != null) && (object == Boolean.TRUE);
    }

    private static class OrExpression<E extends Exception> extends LogicExpression<E>
    {
        public OrExpression(final BooleanExpression<E> lvalue, final BooleanExpression<E> rvalue)
        {
            super(lvalue, rvalue);
        }

        public Object evaluate(Filterable<E> message) throws E
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
    }

    private static class AndExpression<E extends Exception> extends LogicExpression<E>
    {
        public AndExpression(final BooleanExpression<E> lvalue, final BooleanExpression<E> rvalue)
        {
            super(lvalue, rvalue);
        }

        public Object evaluate(Filterable<E> message) throws E
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
    }
}
