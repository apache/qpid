<?xml version='1.0'?> 
<!--
 -
 - Licensed to the Apache Software Foundation (ASF) under one
 - or more contributor license agreements.  See the NOTICE file
 - distributed with this work for additional information
 - regarding copyright ownership.  The ASF licenses this file
 - to you under the Apache License, Version 2.0 (the
 - "License"); you may not use this file except in compliance
 - with the License.  You may obtain a copy of the License at
 - 
 -   http://www.apache.org/licenses/LICENSE-2.0
 - 
 - Unless required by applicable law or agreed to in writing,
 - software distributed under the License is distributed on an
 - "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 - KIND, either express or implied.  See the License for the
 - specific language governing permissions and limitations
 - under the License.
 -
 -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org"> 

  <xsl:import href="code_utils.xsl"/>

  <!--
  =============================
  Template: client-operations-h
  =============================
  Template to generate the AMQP_ClientHandler virtual class. This is the pure
  virtual class from which the AMQP_Server and AMQP_ClientHandlerImpl classes
  are derived.
  -->
  <xsl:template match="amqp" mode="client-operations-h">
    <xsl:param name="domain-cpp-table"/>
    <xsl:result-document href="AMQP_ClientOperations.h" format="textFormat">
      <xsl:value-of select="amqp:copyright()"/>
      <xsl:text>
#ifndef _AMQP_ClientOperations_
#define _AMQP_ClientOperations_

#include "AMQP_Constants.h"
#include "qpid/framing/FieldTable.h"

namespace qpid {
namespace framing {

class AMQP_ClientOperations
{
    public:
        AMQP_ClientOperations() {}
        virtual ~AMQP_ClientOperations() {}
        inline u_int16_t getAmqpMajor() { return (u_int16_t)</xsl:text><xsl:value-of select="@major"/><xsl:text>; }
        inline u_int16_t getAmqpMinor() { return (u_int16_t)</xsl:text><xsl:value-of select="@minor"/><xsl:text>; }&#xA;&#xA;</xsl:text>

      <!-- Inner classes -->
      <xsl:for-each select="class">
        <xsl:variable name="class" select="concat(amqp:cpp-class-name(@name), 'Handler')"/>

        <!-- Inner class documentation & rules -->
        <xsl:if test="doc">
          <xsl:text>&#xA;/**&#xA;===== Class: </xsl:text><xsl:value-of select="$class"/><xsl:text> =====&#xA;</xsl:text>
          <xsl:value-of select="amqp:process-docs(doc)"/>
          <xsl:text>*/&#xA;</xsl:text>
        </xsl:if>

        <!-- Inner class definition -->
        <xsl:text>        class </xsl:text><xsl:value-of select="$class"/><xsl:text>
        {
            public:
                /* Constructors and destructors */
                </xsl:text><xsl:value-of select="$class"/><xsl:text>() {}
                virtual ~</xsl:text><xsl:value-of select="$class"/><xsl:text>() {}
                
                /* Protocol methods */&#xA;</xsl:text>

        <!-- Inner class methods (only if the chassis is set to "client") -->
        <xsl:for-each select="method">
          <xsl:if test="chassis[@name='client']">
            <xsl:variable name="method" select="amqp:cpp-name(@name)"/>

            <!-- Inner class method documentation & rules -->
            <xsl:if test="doc">
              <xsl:text>&#xA;/**&#xA;----- Method: </xsl:text><xsl:value-of select="$class"/><xsl:text>.</xsl:text>
              <xsl:value-of select="@name"/><xsl:text> -----&#xA;</xsl:text>
              <xsl:value-of select="amqp:process-docs(doc)"/>
              <xsl:text>*/&#xA;</xsl:text>
            </xsl:if>
            <xsl:for-each select="rule">
              <xsl:text>&#xA;/**&#xA;</xsl:text>
              <xsl:text>Rule "</xsl:text><xsl:value-of select="@name"/><xsl:text>":&#xA;</xsl:text>
              <xsl:value-of select="amqp:process-docs(doc)"/>
              <xsl:text>*/&#xA;</xsl:text>
            </xsl:for-each>

            <!-- Inner class method definition -->
            <xsl:text>                virtual void </xsl:text><xsl:value-of select="$method"/>
            <xsl:text>( u_int16_t channel</xsl:text>

            <!-- Inner class method parameter definition -->
            <xsl:if test="field">
              <xsl:text>,&#xA;                        </xsl:text>
              <xsl:for-each select="field">
                <xsl:variable name="domain-cpp-type" select="amqp:cpp-lookup(@domain, $domain-cpp-table)"/>
                <xsl:value-of select="concat($domain-cpp-type, amqp:cpp-arg-ref($domain-cpp-type), ' ', amqp:cpp-name(@name))"/>
                <xsl:if test="position()!=last()">
                  <xsl:text>,&#xA;                        </xsl:text>
                </xsl:if>
              </xsl:for-each>
            </xsl:if>
            <xsl:text> ) = 0;&#xA;</xsl:text>
          </xsl:if>
        </xsl:for-each>
        <xsl:text>&#xA;        }; /* class </xsl:text><xsl:value-of select="$class"/><xsl:text> */&#xA;</xsl:text>
      </xsl:for-each>
      <xsl:text>&#xA;}; /* class AMQP_ClientOperations */

} /* namespace framing */
} /* namespace qpid */

#endif&#xA;</xsl:text>
    </xsl:result-document>
  </xsl:template>

</xsl:stylesheet> 
