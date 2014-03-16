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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class represents the base class for all Boolean Expressions created by expanding the Query predicate.
 *
 * @author Fraser Adams
 */
public abstract class BooleanExpression extends Expression
{
    private static Map<String, BooleanExpression> _factories = new HashMap<String, BooleanExpression>();
    protected String[] _operands;
    private String[] _keys;

    /**
     * Initialise the _factories Map, which contains the prototype instances of each concrete BooleanExpression
     * keyed by the operator String.
     */
    static
    {
        _factories.put("eq", new BooleanEquals());
        _factories.put("ne", new BooleanNotEquals());
        _factories.put("lt", new BooleanLessThan());
        _factories.put("le", new BooleanLessEqual());
        _factories.put("gt", new BooleanGreaterThan());
        _factories.put("ge", new BooleanGreaterEqual());
        _factories.put("re_match", new BooleanRegexMatch());
        _factories.put("exists", new BooleanExists());
        _factories.put("true", new BooleanTrue());
        _factories.put("false", new BooleanFalse());
    }

    /**
     * Factory method to create concrete Expression instances based on the operator name extracted from the expression List.
     * This method will create a BooleanExpression from an "eq", "ne", "lt" etc. operator using the prototype 
     * obtained from the _factories Map.
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
        BooleanExpression factory = _factories.get(op);
        if (factory == null)
        {
            throw new QmfException("Unknown operator in predicate expression");
        }

        return factory.create(expr);
    }

    /**
     * Factory method to create a concrete instance of BooleanExpression
     * @param expr the List of Expressions extracted by parsing the Query predicate
     * @return an instance of the concrete BooleanExpression
     */
    public abstract Expression create(final List expr) throws QmfException;

    /**
     * Basic Constructor primarily used by the prototype instance of each concrete BooleanExpression
     */
    protected BooleanExpression()
    {
    }

    /**
     * Main Constructor, used to populate unevaluated operands. This loops through the input expression list. If the
     * Object is a String is is treated as a key such that when the expression is evaluated the key will be used to
     * obtain a propery from the QmfData object. If the Object is a sub-List it is checked to see if it's a quoted
     * String, if it is the quoted String is stored as the operand. If it's neither of these the actual object from
     * the expression List is used as the operand.
     *
     * @param operandCount the number of operands in this Expression, the value is generally passed by the subclass.
     * @param expr the List of Expressions extracted by parsing the Query predicate
     */
    protected BooleanExpression(final int operandCount, final List expr) throws QmfException
    {
        Iterator iter = expr.listIterator();
        String op = (String)iter.next(); // We've already tested for hasNext() in the factory

        _operands = new String[operandCount];
        _keys = new String[operandCount];

        for (int i = 0; i < operandCount; i++)
        {
            if (!iter.hasNext())
            {
                   throw new QmfException("Too few operands for operation: " + op);
            }

            Object object = iter.next();
            _operands[i] = object.toString();

            if (object instanceof String)
            {
                _keys[i] = _operands[i];
                _operands[i] = null;
            }
            else if (object instanceof List)
            {
                List sublist = (List)object;
                Iterator subiter = sublist.listIterator();

                if (subiter.hasNext() && ((String)subiter.next()).equals("quote"))
                {
                    if (subiter.hasNext())
                    {
                        _operands[i] = subiter.next().toString();
                        if (subiter.hasNext())
                        {
                             throw new QmfException("Extra tokens at end of 'quote'");
                        }
                    }
                }
                else
                {
                    throw new QmfException("Expected '[quote, <token>]'");
                }
            }
        }

        if (iter.hasNext())
        {
            throw new QmfException("Too many operands for operation: " + op);
        }
    }

    /**
     * Populates operands that are obtained at evaluation time. In other words operands that are obtained by using
     * the key obtained from the static operand evaluation to look up an associated property from the QmfData object.
     * @param data the object to extract the operand(s) from
     */
    protected void populateOperands(final QmfData data)
    {
        for (int i = 0; i < _operands.length; i++)
        {
            String key = _keys[i];
            if (key != null)
            {
                String value = null;

                if (data.hasValue(key))
                { // If there's a property of the data object named key look it up as a String
                    value = data.getStringValue(key);
                }
                else
                { // If there's no property of the data object named key look up its Described/Managed metadata
                    if (data instanceof QmfManaged)
                    {
                        QmfManaged managedData = (QmfManaged)data;
                        if (key.equals("_schema_id"))
                        {
                            value = managedData.getSchemaClassId().toString();
                        }
                        else if (key.equals("_object_id"))
                        {
                            value = managedData.getObjectId().toString();
                        }
                        else if (managedData.getSchemaClassId().hasValue(key))
                        { // If it's not _schema_id or _object_id check the SchemaClassId properties e.g. 
                          // _package_name, _class_name, _type or _hash
                            value = managedData.getSchemaClassId().getStringValue(key);
                        }
                    }

                    if (value == null)
                    { // If a value still can't be found for the key check if it's available in the mapEncoded form
                        Map m = data.mapEncode();
                        if (m.containsKey(key))
                        {
                            value = QmfData.getString(m.get(key));
                        }
                    }
                }

                _operands[i] = value;
            }
//System.out.println("key: " + key + ", operand = " + _operands[i]);
        }
    }

    /**
     * Evaluate expression against a QmfData instance.
     * @param data the object to evaluate the expression against
     * @return true if query matches the QmfData instance, else false.
     */
    public abstract boolean evaluate(final QmfData data);
}

