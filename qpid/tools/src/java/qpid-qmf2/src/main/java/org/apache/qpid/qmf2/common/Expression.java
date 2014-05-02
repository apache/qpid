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
package org.apache.qpid.qmf2.common;

// Misc Imports
import java.util.Iterator;
import java.util.List;

/**
 * This class represents the base class for all Expressions created by expanding the Query predicate.
 * <p>
 * Depending on the structure of the expression list there might be a nested structure of Expressions comprising
 * a mixture of LogicalExpressions and BooleanExpressions.
 * <p>
 * The Expression structure is illustrated below in the context of its relationship with QmfQuery.
 * <img src="doc-files/QmfQuery.png"/>
 *
 * @author Fraser Adams
 */
public abstract class Expression
{
    /**
     * Factory method to create concrete Expression instances base on the operator name extracted from the expression List.
     * This method will create a LogicalExpression from an "and", "or" or "not" operator otherwise it will create
     * a BooleanExpression.
     *
     * @param expr the List of Expressions extracted by parsing the Query predicate
     */
    public static Expression createExpression(final List expr) throws QmfException
    {
        Iterator iter = expr.listIterator();
        if (!iter.hasNext())
        {
            throw new QmfException("Missing operator in predicate expression");
        }

        String op = (String)iter.next();
        if (op.equals("not"))
        {
            return new LogicalNot(expr);
        }
        if (op.equals("and"))
        {
            return new LogicalAnd(expr);
        }
        if (op.equals("or"))
        {
            return new LogicalOr(expr);
        }
        return BooleanExpression.createExpression(expr);
    }    

    /**
     * Evaluate expression against a QmfData instance.
     * @return true if query matches the QmfData instance, else false.
     */
    public abstract boolean evaluate(final QmfData data);
}

