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

package org.apache.qpid.ra;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import javax.jms.ConnectionMetaData;

import org.apache.qpid.client.CustomJMSXProperty;
import org.apache.qpid.common.QpidProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements javax.jms.ConnectionMetaData
 *
 */
public class QpidRAConnectionMetaData implements ConnectionMetaData
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAConnectionMetaData.class);

   private static final String PROVIDER_VERSION ;
   private static final int PROVIDER_MAJOR ;
   private static final int PROVIDER_MINOR ;
   private static final String[] JMSX_PROPERTY_NAMES ;

   /**
    * Constructor
    */
   public QpidRAConnectionMetaData()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor()");
      }
   }

   /**
    * Get the JMS version
    * @return The version
    */
   public String getJMSVersion()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getJMSVersion()");
      }

      return "1.1";
   }

   /**
    * Get the JMS major version
    * @return The major version
    */
   public int getJMSMajorVersion()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getJMSMajorVersion()");
      }

      return 1;
   }

   /**
    * Get the JMS minor version
    * @return The minor version
    */
   public int getJMSMinorVersion()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getJMSMinorVersion()");
      }

      return 1;
   }

   /**
    * Get the JMS provider name
    * @return The name
    */
   public String getJMSProviderName()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getJMSProviderName()");
      }

      return QpidProperties.getProductName() + " Resource Adapter" ;
   }

   /**
    * Get the provider version
    * @return The version
    */
   public String getProviderVersion()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getProviderVersion()");
      }

      return PROVIDER_VERSION ;
   }

   /**
    * Get the provider major version
    * @return The version
    */
   public int getProviderMajorVersion()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getProviderMajorVersion()");
      }

      return PROVIDER_MAJOR ;
   }

   /**
    * Get the provider minor version
    * @return The version
    */
   public int getProviderMinorVersion()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getProviderMinorVersion()");
      }

      return PROVIDER_MINOR ;
   }

   /**
    * Get the JMS XPropertyNames
    * @return The names
    */
   public Enumeration<String> getJMSXPropertyNames()
   {
      // Bug in CustomJMSXProperty.asEnumeration() so we handle this here
      return Collections.enumeration(Arrays.asList(JMSX_PROPERTY_NAMES)) ;
   }

   static
   {
	   final String version = QpidProperties.getReleaseVersion() ;
	   int major = -1 ;
	   int minor = -1 ;
	   if (version != null)
	   {
		   final int separator = version.indexOf('.') ;
		   if (separator != -1)
		   {
			   major = parseInt(version.substring(0, separator), "major") ;
			   minor = parseInt(version.substring(separator+1, version.length()), "minor") ;
		   }
	   }
	   PROVIDER_VERSION = version ;
	   PROVIDER_MAJOR = major ;
	   PROVIDER_MINOR = minor ;

      final CustomJMSXProperty[] properties = CustomJMSXProperty.values();
      final String[] names = new String[properties.length] ;
      int count = 0 ;
      for(CustomJMSXProperty property :  properties)
      {
          names[count++] = property.toString() ;
      }
	   JMSX_PROPERTY_NAMES = names ;
   }

   private static int parseInt(final String value, final String name)
   {
	   try
	   {
		   return Integer.parseInt(value) ;
	   }
	   catch (final NumberFormatException nfe)
	   {
		   _log.warn("Failed to parse " + name + ": " + value) ;
		   return -1 ;
	   }
   }
}
