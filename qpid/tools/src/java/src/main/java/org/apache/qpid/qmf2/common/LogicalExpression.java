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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*
 * This class represents the base class for all Logical Expressions (and, or, not) created by expanding the Query predicate.
 *
 * @author Fraser Adams
 */
public abstract class LogicalExpression extends Expression
{
    protected List<Expression> _subExpressions = new ArrayList<Expression>();

    /**
     * Constructor. This method iterates through collecting the sub-expressions of the Logical Expression
     *
     * @param expr the List of sub-expressions extracted by parsing the Query predicate, the first one should be
     *        the Logical Expression's operator name
     */
    public LogicalExpression(final List expr) throws QmfException
    {
        Iterator iter = expr.listIterator();
        String op = (String)iter.next();
//System.out.println("LogicalExpression, op = " + op);

        // Collect sub-expressions
        while (iter.hasNext())
        {
            Object object = iter.next();
            if (object instanceof List)
            {
                _subExpressions.add(createExpression((List)object));
            }
            else
            {
                throw new QmfException("Operands of " + op + " must be Lists");
            }
        }
    }
}

