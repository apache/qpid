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
import java.util.List;

/**
 * A class to create and evaluate the BooleanGreaterThan Expression
 *
 * @author Fraser Adams
 */
public final class BooleanGreaterThan extends BooleanExpression
{
    /**
     * Factory method to create an instance of BooleanGreaterThan
     * @param expr the List of Expressions extracted by parsing the Query predicate
     * @return an instance of the concrete BooleanExpression
     */
    public Expression create(final List expr) throws QmfException
    {
        return new BooleanGreaterThan(expr);
    }

    /**
     * Basic Constructor primarily used by the prototype instance of each concrete BooleanExpression
     */
    public BooleanGreaterThan()
    {
    }

    /**
     * Main Constructor, uses base class constructor to populate unevaluated operands
     * @param expr the List of Expressions extracted by parsing the Query predicate
     */
    public BooleanGreaterThan(final List expr) throws QmfException
    {
        super(2, expr);
    }

    /**
     * Evaluate "greater than" expression against a QmfData instance.
     * N.B. to avoid complexities with types this class treats operands as Strings performing an appropriate evaluation
     * of the String that makes sense for a given expression e.g. parsing as a double for >, >=, <, <=
     *
     * @param data the object to evaluate the expression against
     * @return true if query matches the QmfData instance, else false.
     */    
    public boolean evaluate(QmfData data)
    {
        populateOperands(data);

        if (_operands[0] == null || _operands[1] == null)
        {
            return false;
        }

        try
        {
            double l = Double.parseDouble(_operands[0]);
            double r = Double.parseDouble(_operands[1]);
            return l > r;
        }
        catch (NumberFormatException nfe)
        {
            // If converting to double fails try a lexicographic comparison
            return _operands[0].compareTo(_operands[1]) > 0;
        }
    }
}

