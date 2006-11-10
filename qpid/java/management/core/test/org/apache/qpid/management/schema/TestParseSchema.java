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
package org.apache.qpid.management.schema;

import org.apache.log4j.Logger;
import org.apache.qpid.management.ManagementConnection;
import org.apache.qpid.management.messaging.CMLMessageFactory;
import org.apache.qpid.schema.cml.CmlDocument;

import javax.jms.TextMessage;

public class TestParseSchema
{
    private static final Logger _logger = Logger.getLogger(TestParseSchema.class);

    private static ManagementConnection _con;

    private static void parseCMLSchema(String xml) throws Exception
    {
        CmlDocument cmlDoc = CmlDocument.Factory.parse(xml);
        CmlDocument.Cml cml = cmlDoc.getCml();
        /*SchemaReplyDocument.SchemaReply schema = cml.getSchemaReply();
        for (ClassDocument.Class classDefn: schema.getClass1List())
        {
            System.out.println("Class: " + classDefn.getName());
        } */
    }

    public static void main(String[] args)
    {
        _logger.info("Starting...");

        if (args.length != 5)
        {
            System.out.println("Usage: host port username password vhost");
            System.exit(1);
        }
        try
        {
            _con = new ManagementConnection(args[0], Integer.parseInt(args[1]), args[2], args[3],
                                            args[4]);

            _con.connect();
            TextMessage tm = _con.sendRequest(CMLMessageFactory.createSchemaRequest());
            parseCMLSchema(tm.getText());
            _logger.info("Closing management connection");
            _con.close();

            //_logger.info("Waiting...");
        }
        catch (Throwable t)
        {
            _logger.error("Fatal error: " + t, t);
        }
        finally
        {
            if (_con != null)
            {
                _logger.info("Closing connection");
                try
                {
                    _con.close();
                }
                catch (Exception e)
                {
                    _logger.error("Error closing connection: " + e);
                }
            }
        }

    }
}
