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

// Reuse this class as it provides a handy mechanism to parse an predicate String into a Map
import org.apache.qpid.messaging.util.AddressParser;

/**
 * A Query is a mechanism for interrogating the management database. A Query represents a selector which is sent to
 * an Agent. The Agent applies the Query against its management database, and returns those objects which meet the  
 * constraints described in the query.
 * <p>
 * A Query must specify the class of information it is selecting. This class of information is considered the target
 * of the query. Any data objects selected by the query will be of the type indicated by the target.
 * <p>
 * A Query may also specify a selector which is used as a filter against the set of all target instances. Only those 
 * instances accepted by the filter will be returned in response to the query.
 * <p>
 * N.B. There appear to be a number of differences in the description of the map encoding of a Query between the
 * QMF2 API specified at <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> and the
 * QMF2 protocol that is specified at <a href=https://cwiki.apache.org/qpid/qmf-map-message-protocol.html>QMF Map
 * Message Protocol</a> in particular the use of the underscore to specify key names e.g. "_what", "_where",
 * "_object_id", "_schema_id".
 * <p>
 * This implementation trusts the protocol specification more than the API specification as the underscores are more  
 * consistent with the rest of the protocol and the underscored variants are what have been observed when querying
 * the broker ManagementAgent.
 * <p>
 * A QmfQuery may be constructed as either an "ID" query (to query for a specific ObjectId or SchemaClassId) or a
 * "PREDICATE" query (to query based upon an expression). Note that QMF considers string arguments in boolean
 * expressions to be names of data values in the target object. When evaluating a predicate expression, QMF will fetch
 * the value of the named data item from each candidate target object. The value is then used in the boolean expression.
 * In other words, QMF considers string arguments to be variables in the expression. In order to indicate that a string 
 * should be treated as a literal instead, the string must be quoted using the "quote" expression.
 * <p>
 * <b>Examples</b>
 * <p>
 * Assume a QmfData type defines fields named "name", "address" and "town". The following predicate expression matches
 * any instance with a name field set to "tross", or any instance where the name field is "jross", the address field is
 * "1313 Spudboy Lane" and the town field is "Utopia":
 * <p>
 * <pre>
 * ["or" ["eq" "name" ["quote" "tross"]]
 *       ["and" ["eq" "name" ["quote" "jross"]]
 *              ["eq" "address" ["quote" "1313 Spudboy Lane"]]
 *              ["eq" ["quote" "Utopia"] "town"]
 *     ]
 * ]
 * </pre>
 * Assume a QmfData type with fields "name" and "age". A predicate to find all instances with name matching the regular 
 * expression "?ross" with an optional age field that is greater than the value 29 or less than 12 would be:
 * <pre>
 * ["and" ["re_match" "name" ["quote" "?ross"]]
 *        ["and" ["exists" "age"]
 *               ["or" ["gt" "age" 27] ["lt" "age" 12]]
 *        ]
 * ]
 * </pre>
 * <p>
 * The Expression structure is illustrated below in the context of its relationship with QmfQuery. 
 * <img src="doc-files/QmfQuery.png"/>
 *
 *
 * @author Fraser Adams
 */
public final class QmfQuery extends QmfData
{
    public static final QmfQuery ID = new QmfQuery();
    public static final QmfQuery PREDICATE = new QmfQuery();

    private QmfQueryTarget _target;
    private SchemaClassId  _classId;
    private String         _packageName;
    private String         _className;
    private ObjectId       _objectId;
    private List           _predicate;
    private Expression     _expression;

    /**
     * This Constructor is only used to construct the ID and PREDICATE objects
     */
    private QmfQuery()
    {
    }

    /**
     * Construct an QmfQuery with no Selector from a QmfQueryTarget
     * @param target the query target
     */
    public QmfQuery(final QmfQueryTarget target)
    {
        _target = target;
        setValue("_what", _target.toString());
    }

    /**
     * Construct an ID QmfQuery from a QmfQueryTarget and SchemaClassId
     * @param target the query target
     * @param classId the SchemaClassId to evaluate against
     */
    public QmfQuery(final QmfQueryTarget target, final SchemaClassId classId)
    {
        _target = target;
        _classId = classId;
        _packageName = _classId.getPackageName();
        _className = _classId.getClassName();
        setValue("_what", _target.toString());
        setValue("_schema_id", _classId.mapEncode());
    }

    /**
     * Construct an ID QmfQuery from a QmfQueryTarget and ObjectId
     * @param target the query target
     * @param objectId the ObjectId to evaluate against
     */
    public QmfQuery(final QmfQueryTarget target, final ObjectId objectId)
    {
        _target = target;
        _objectId = objectId;
        setValue("_what", _target.toString());
        setValue("_object_id", _objectId.mapEncode());
    }

    /**
     * Construct a PREDICATE QmfQuery from a QmfQueryTarget and predicate String
     * @param target the query target
     * @param predicateString the predicate to evaluate against
     */
    public QmfQuery(final QmfQueryTarget target, final String predicateString) throws QmfException
    {
        _target = target;

        if (predicateString.charAt(0) == '[')
        {
            Map predicateMap = new AddressParser("{'_where': " + predicateString + "}").map();
            _predicate = (List)predicateMap.get("_where");
            _expression = Expression.createExpression(_predicate);
        }
        else
        {
            throw new QmfException("Invalid predicate format");
        }

        setValue("_what", _target.toString());
        setValue("_where", _predicate);
    }

    /**
     * Construct a QmfQuery from a Map encoding
     * @param m encoding the query
     */
    public QmfQuery(final Map m) throws QmfException
    {
        super(m);

        _target = QmfQueryTarget.valueOf(getStringValue("_what"));

        if (hasValue("_object_id"))
        {
            _objectId = getRefValue("_object_id");
        }

        if (hasValue("_schema_id"))
        {
            _classId = new SchemaClassId((Map)getValue("_schema_id"));
            _packageName = _classId.getPackageName();
            _className = _classId.getClassName();
        }

        if (hasValue("_where"))
        {
            _predicate = (List)getValue("_where");
            _expression = Expression.createExpression(_predicate);
        }
    }

    /**
     * Return target name.
     * @return target name.
     */
    public QmfQueryTarget getTarget()
    {
        return _target;
    }

    /**
     * Undefined by QMF2 API.
     * <p>
     * According to <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Specification</a>
     * "The value of the <target name string> map entry is ignored for now, its use is TBD."
     * so this method returns a null Map.
     */
    public Map getTargetParam()
    {
        return null;
    }

    /**
     * Return QmfQuery.ID or QmfQuery.PREDICATE or null if there is no Selector
     * @return QmfQuery.ID or QmfQuery.PREDICATE or null if there is no Selector
     */
    public QmfQuery getSelector()
    {
        if (_predicate == null)
        {
            if (_objectId == null && _classId == null)
            {
                return null;
            }
            return ID;
        }
        return PREDICATE;
    }

    /**
     * Return predicate expression if selector type is QmfQuery.PREDICATE
     * @return predicate expression if selector type is QmfQuery.PREDICATE
     */
    public List getPredicate()
    {
        return _predicate;
    }

    /**
     * Return the SchemaClassId if selector type is QmfQuery.ID
     * @return the SchemaClassId if selector type is QmfQuery.ID
     */
    public SchemaClassId getSchemaClassId()
    {
        return _classId;
    }

    /**
     * Return the ObjectId if selector type is QmfQuery.ID
     * @return the ObjectId if selector type is QmfQuery.ID
     */
    public ObjectId getObjectId()
    {
        return _objectId;
    }

    /**
     * Evaluate query against a QmfData instance.
     * @return true if query matches the QmfData instance, else false.
     */
    public boolean evaluate(final QmfData data)
    {
        if (_predicate == null)
        {
            if (data instanceof QmfManaged)
            {
                QmfManaged managedData = (QmfManaged)data;
                // Evaluate an ID query on Managed Data
                if (_objectId != null && _objectId.equals(managedData.getObjectId()))
                {
                    return true;
                }
                else if (_classId != null)
                {
                    SchemaClassId dataClassId = managedData.getSchemaClassId();
                    String dataClassName = dataClassId.getClassName();
                    String dataPackageName = dataClassId.getPackageName();

                    // Wildcard the package name if it hasn't been specified when checking class name
                    if (_className.equals(dataClassName) &&
                        (_packageName.length() == 0 || _packageName.equals(dataPackageName)))
                    {
                        return true;
                    }

                    // Wildcard the class name if it hasn't been specified when checking package name
                    if (_packageName.equals(dataPackageName) &&
                        (_className.length() == 0 || _className.equals(dataClassName)))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        else
        {
            // Evaluate a PREDICATE query by evaluating against the expression created from the predicate
            if (_predicate.size() == 0)
            {
                return true;
            }

            return _expression.evaluate(data);
        }
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        System.out.println("QmfQuery:");
        System.out.println("target: " + _target);
        if (_predicate != null)
        {
            System.out.println("selector: QmfQuery.PREDICATE");
            System.out.println("predicate: " + _predicate);
        }
        else if (_classId != null)
        {
            System.out.println("selector: QmfQuery.ID");
            _classId.listValues();
        }
        else if (_objectId != null)
        {
            System.out.println("selector: QmfQuery.ID");
            System.out.println(_objectId);
        }
    }
}

