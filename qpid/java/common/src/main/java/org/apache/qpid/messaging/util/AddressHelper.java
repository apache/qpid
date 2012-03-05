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
package org.apache.qpid.messaging.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.configuration.Accessor;
import org.apache.qpid.configuration.Accessor.MapAccessor;
import org.apache.qpid.messaging.Address;

/**
* Utility class for extracting information from the address class
*/
public class AddressHelper
{
   public static final String NODE = "node";
   public static final String LINK = "link";
   public static final String X_DECLARE = "x-declare";
   public static final String X_BINDINGS = "x-bindings";
   public static final String X_SUBSCRIBE = "x-subscribes";
   public static final String CREATE = "create";
   public static final String ASSERT = "assert";
   public static final String DELETE = "delete";
   public static final String FILTER = "filter";
   public static final String NO_LOCAL = "no-local";
   public static final String DURABLE = "durable";
   public static final String EXCLUSIVE = "exclusive";
   public static final String AUTO_DELETE = "auto-delete";
   public static final String TYPE = "type";
   public static final String ALT_EXCHANGE = "alternate-exchange";
   public static final String BINDINGS = "bindings";
   public static final String BROWSE = "browse";
   public static final String MODE = "mode";
   public static final String CAPACITY = "capacity";
   public static final String CAPACITY_SOURCE = "source";
   public static final String CAPACITY_TARGET = "target";
   public static final String NAME = "name";
   public static final String EXCHANGE = "exchange";
   public static final String QUEUE = "queue";
   public static final String KEY = "key";
   public static final String ARGUMENTS = "arguments";
   public static final String RELIABILITY = "reliability";

   private Address address;
   private MapAccessor addressProps;
   private MapAccessor nodeProps;
   private MapAccessor linkProps;

   public AddressHelper(Address address)
   {
       this.address = address;
       addressProps = new MapAccessor(address.getOptions());
       Map node_props = address.getOptions() == null
               || address.getOptions().get(NODE) == null ? null
               : (Map) address.getOptions().get(NODE);

       if (node_props != null)
       {
           nodeProps = new MapAccessor(node_props);
       }

       Map link_props = address.getOptions() == null
               || address.getOptions().get(LINK) == null ? null
               : (Map) address.getOptions().get(LINK);

       if (link_props != null)
       {
           linkProps = new MapAccessor(link_props);
       }
   }

   public String getCreate()
   {
       return addressProps.getString(CREATE);
   }

   public String getAssert()
   {
       return addressProps.getString(ASSERT);
   }

   public String getDelete()
   {
       return addressProps.getString(DELETE);
   }

   public boolean isNodeMarkedNoLocal()
   {
       Boolean b = nodeProps.getBoolean(NO_LOCAL);
       return b == null ? false : b;
   }

   public boolean isBrowseOnly()
   {
       String mode = addressProps.getString(MODE);
       return mode != null && mode.equals(BROWSE) ? true : false;
   }

   @SuppressWarnings("unchecked")
   public List<Object> getNodeBindings()
   {
       return (List<Object>) nodeProps.getList(X_BINDINGS);
   }

   public Map getDeclareArgs(Map props)
   {
       if (props != null && props.get(X_DECLARE) != null)
       {
           return (Map) props.get(X_DECLARE);
       }
       else
       {
           return Collections.EMPTY_MAP;
       }
   }

   private boolean getDurability(Map map)
   {
       Accessor access = new MapAccessor(map);
       Boolean result = access.getBoolean(DURABLE);
       return (result == null) ? false : result.booleanValue();
   }
}
