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
 * A class to evaluate the LogicalAnd Expression
 *
 * @author Fraser Adams
 */

public final class LogicalAnd extends LogicalExpression
{
    /**
     * This method iterates through collecting the sub-expressions of the Logical Expression
     *
     * @param expr the List of sub-expressions extracted by parsing the Query predicate, the first one should be
     *        the Logical Expression's operator name
     */
    public LogicalAnd(final List expr) throws QmfException
    {
        super(expr);
    }

    /**
     * Evaluate the Logical And expression against a QmfData instance.
     * @return false if any of the sub-expressions is false otherwise returns true
     */
    public boolean evaluate(final QmfData data)
    {
        for (Expression e : _subExpressions)
        {
            if (!e.evaluate(data))
            {
                return false;
            }
        }
        return true;
    }
}


