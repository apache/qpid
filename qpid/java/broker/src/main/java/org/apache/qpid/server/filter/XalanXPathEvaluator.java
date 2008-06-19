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

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.Filterable;
import org.apache.xpath.CachedXPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.InputSource;

public class XalanXPathEvaluator implements XPathExpression.XPathEvaluator {
    
    private final String xpath;

    public XalanXPathEvaluator(String xpath) {
        this.xpath = xpath;
    }
    
    public boolean evaluate(Filterable m) throws AMQException
    {
        // TODO - we would have to check the content type and then evaluate the content
        //        here... is this really a feature we wish to implement? - RobG
        /*

        if( m instanceof TextMessage ) {
            String text = ((TextMessage)m).getText();
            return evaluate(text);
        } else if ( m instanceof BytesMessage ) {
            BytesMessage bm = (BytesMessage) m;
            byte data[] = new byte[(int) bm.getBodyLength()];
            bm.readBytes(data);
            return evaluate(data);
        }
        */
        return false;

    }

    private boolean evaluate(byte[] data) {
        try {
            
            InputSource inputSource = new InputSource(new ByteArrayInputStream(data));
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder dbuilder = factory.newDocumentBuilder();
            Document doc = dbuilder.parse(inputSource);
            
            CachedXPathAPI cachedXPathAPI = new CachedXPathAPI();
            NodeIterator iterator = cachedXPathAPI.selectNodeIterator(doc,xpath);
            return iterator.nextNode()!=null;
            
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean evaluate(String text) {
        try {
            InputSource inputSource = new InputSource(new StringReader(text));
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder dbuilder = factory.newDocumentBuilder();
            Document doc = dbuilder.parse(inputSource);
            
            // We should associated the cachedXPathAPI object with the message being evaluated
            // since that should speedup subsequent xpath expressions.
            CachedXPathAPI cachedXPathAPI = new CachedXPathAPI();
            NodeIterator iterator = cachedXPathAPI.selectNodeIterator(doc,xpath);
            return iterator.nextNode()!=null;
        } catch (Throwable e) {
            return false;
        }
    }
}
