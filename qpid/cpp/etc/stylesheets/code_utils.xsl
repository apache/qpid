<?xml version='1.0'?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org">

  <!--
  ========================
  Function: amqp:copyright
  ========================
  Print out a standard Apache copyright notice and generated code warning.
  -->
  <xsl:function name="amqp:copyright">//
//
// Copyright (c) 2006 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

 
//
//
// NOTE: This file is generated directly from the AMQP XML specification.
// === DO NOT EDIT ===
//
//&#xA;</xsl:function>

  <!--
  ==========================
  Function: amqp:upper-first
  ==========================
  Convert the first character of the parameter to upper-case
  -->
  <xsl:function name="amqp:upper-first">
    <xsl:param name="in"/>
    <xsl:value-of select="concat(upper-case(substring($in, 1, 1)), substring($in, 2))"/>
  </xsl:function>

  <!--
  ========================
  Function: amqp:cpp-name-1
  ========================
  Convert parameter "name" to a valid C++ identifier, finding spaces and '-'s
  in the parameter name and replacing them with '_' chars. Also check for C++
  reserved words and prefix them with '_'. No capitalization is performed.
  -->
  <xsl:function name="amqp:cpp-name-1">
    <xsl:param name="name"/>
    <xsl:choose>
      <!-- C++ reserved words. -->
      <xsl:when test="$name='delete'">delete_</xsl:when>
      <xsl:when test="$name='return'">return_</xsl:when>
      <!-- Change unsuitable C++ identifier characters. -->
      <xsl:otherwise><xsl:value-of select="translate($name, ' -', '__')"/></xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <!--
  =======================
  Function: amqp:cpp-name
  =======================
  Convert parameter "name" to a valid, camel cased C++ name. 
  -->
  <xsl:function name="amqp:cpp-name">
    <xsl:param name="name"/>
    <xsl:value-of select="amqp:cpp-name-1(amqp:camel-case($name))"/>
  </xsl:function>

  <!--
  =============================
  Function: amqp:cpp-class-name
  =============================
  Convert parameter "name" to a valid C++ identifier, finding spaces and '-'s
  in the parameter name and replacing them with '_' chars. Also check for C++
  reserved words and prefix them with '_'. First letter only is capitalized.
  -->
  <xsl:function name="amqp:cpp-class-name">
    <xsl:param name="name"/>
    <xsl:value-of select="amqp:upper-first(amqp:cpp-name($name))"/>
  </xsl:function>

  <!--
  =========================
  Function: amqp:camel-case
  =========================
  *** NOTE: Only works with *one* of either '-' or ' '. If a name contains 2 or
  *** more of these characters, then this will break.
  Convert parameter "name" to camel case, where words are separated by ' ' or '-'
  -->
  <xsl:function name="amqp:camel-case">
    <xsl:param name="name"/>
    <xsl:choose>
        <xsl:when test="contains($name, ' ')">
            <xsl:value-of select="concat(substring-before($name, ' '), amqp:upper-first(substring-after($name, ' ')))"/>
        </xsl:when>
        <xsl:when test="contains($name, '-')">
            <xsl:value-of select="concat(substring-before($name, '-'), amqp:upper-first(substring-after($name, '-')))"/>
        </xsl:when>
        <xsl:otherwise>
            <xsl:value-of select="$name"/>
        </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <!--
  =========================
  Function: amqp:field-name
  =========================
  Get a valid field name, processing spaces and '-'s where appropriate
  -->
  <xsl:function name="amqp:field-name">
    <xsl:param name="name"/>
    <xsl:value-of select="amqp:upper-first(amqp:camel-case($name))"/>
  </xsl:function>

  <!--
  =======================
  Function: amqp:cpp-type
  =======================
  Map the set of simple AMQP types to C++ types. Also map the AMQP table
  domain to appropriate C++ class.
  -->
  <xsl:function name="amqp:cpp-type">
    <xsl:param name="type"/>
    <xsl:choose>
      <!-- Simple AMQP domain types -->
      <xsl:when test="$type='octet'">u_int8_t</xsl:when>
      <xsl:when test="$type='short'">u_int16_t</xsl:when>
      <xsl:when test="$type='shortstr'">string</xsl:when>
      <xsl:when test="$type='longstr'">string</xsl:when>
      <xsl:when test="$type='bit'">bool</xsl:when>
      <xsl:when test="$type='long'">u_int32_t</xsl:when>
      <xsl:when test="$type='longlong'">u_int64_t</xsl:when>
      <xsl:when test="$type='timestamp'">u_int64_t</xsl:when>
      <!-- AMQP structures -->
      <xsl:when test="$type='table'">FieldTable</xsl:when>
      <!-- Fallback: unknown type -->
      <xsl:otherwise>unknown_type /* WARNING: undefined type */</xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <!--
  ==========================
  Function: amqp:cpp-arg-ref
  ==========================
  Determines whether a C++ reference is required for an argument.
  -->
  <xsl:function name="amqp:cpp-arg-ref">
    <xsl:param name="type"/>
    <xsl:choose>
      <xsl:when test="$type='string'">&amp;</xsl:when>
      <xsl:when test="$type='FieldTable'">&amp;</xsl:when>
    </xsl:choose>
  </xsl:function>

  <!--
  =========================
  Function: amqp:cpp-lookup
  =========================
  Template and function for looking up the cpp type from the domain name.
  The template runs on a lookup table XML generated by the "domain_table"
  template in amqp_domaintypes.xsl.
  -->
  <xsl:template match="/" mode="cpp-lookup">
    <xsl:param name="domain-name"/>
    <xsl:for-each select="key('domain-lookup', $domain-name)">
      <xsl:value-of select="@cpp-type"/>
    </xsl:for-each>
  </xsl:template>

  <xsl:function name="amqp:cpp-lookup">
    <xsl:param name="domain-name"/>
    <xsl:param name="domain-cpp-table"/>
    <xsl:apply-templates mode="cpp-lookup" select="$domain-cpp-table">
      <xsl:with-param name="domain-name" select="$domain-name"/>
    </xsl:apply-templates>
  </xsl:function>

  <!--
  =========================
  Function: amqp:cpp-lookup
  =========================
  Template and function for processing the possibly multiple <doc> elements
  within a node.
  -->
  <xsl:template match="doc" mode="process-doc-elts">
    <xsl:for-each select=".">
      <xsl:choose>
        <xsl:when test=".[@type='grammar']"><xsl:value-of select="."/></xsl:when>
        <xsl:when test=".[@type='scenario']"><xsl:value-of select="concat('&#xA;Test Scenario: ', normalize-space(.))"/></xsl:when>
        <xsl:otherwise><xsl:value-of select="normalize-space(.)"/></xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>

  <xsl:function name="amqp:process-docs">
    <xsl:param name="doc-elts"/>
    <xsl:apply-templates mode="process-doc-elts" select="$doc-elts"/>
  </xsl:function>


</xsl:stylesheet>

