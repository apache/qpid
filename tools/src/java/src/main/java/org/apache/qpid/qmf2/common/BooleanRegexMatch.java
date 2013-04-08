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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A class to create and evaluate the BooleanRegexMatch Expression
 *
 * @author Fraser Adams
 */
public final class BooleanRegexMatch extends BooleanExpression
{
    private final Pattern _pattern;

    /**
     * Factory method to create an instance of BooleanRegexMatch
     * @param expr the List of Expressions extracted by parsing the Query predicate
     * @return an instance of the concrete BooleanExpression
     */
    public Expression create(final List expr) throws QmfException
    {
        return new BooleanRegexMatch(expr);
    }

    /**
     * Basic Constructor primarily used by the prototype instance of each concrete BooleanExpression
     */
    public BooleanRegexMatch()
    {
        _pattern = null;
    }

    /**
     * Main Constructor, uses base class constructor to populate unevaluated operands
     * @param expr the List of Expressions extracted by parsing the Query predicate
     */
    public BooleanRegexMatch(final List expr) throws QmfException
    {
        super(2, expr);

        try
        {
            _pattern = Pattern.compile(_operands[1]);
        }
        catch (PatternSyntaxException pse)
        {
            throw new QmfException("Error in regular expression " + pse.getMessage());
        }
    }
    
    /**
     * Evaluate "regex match" expression against a QmfData instance.
     * N.B. to avoid complexities with types this class treats operands as Strings performing an appropriate evaluation
     * of the String that makes sense for a given expression e.g. parsing as a double for >, >=, <, <=
     *
     * @param data the object to evaluate the expression against
     * @return true if query matches the QmfData instance, else false.
     */
    public boolean evaluate(final QmfData data)
    {
        populateOperands(data);

        if (_operands[0] == null || _operands[1] == null || _pattern == null)
        {
            return false;
        }

        Matcher matcher = _pattern.matcher(_operands[0]);
        return matcher.find();
    }
}

