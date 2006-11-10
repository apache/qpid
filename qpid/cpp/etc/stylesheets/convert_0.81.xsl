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

<xsl:template match="/">
    <xsl:apply-templates select="/" mode="do-amqp"/> 
</xsl:template> 

<!-- ======
     <amqp>
     ====== -->
<xsl:template match="amqp" mode="do-amqp">

<!-- <xsl:text>&#xA;</xsl:text> -->
<xsl:element name= "amqp">
<xsl:attribute name="major"><xsl:value-of select="@major"/></xsl:attribute>
<xsl:attribute name="minor"><xsl:value-of select="@minor"/></xsl:attribute>
<xsl:attribute name="port"><xsl:value-of select="@port"/></xsl:attribute>
<xsl:attribute name="comment"><xsl:value-of select="@comment"/></xsl:attribute>
<xsl:text>&#xA;</xsl:text>

<!-- constant elements -->
<xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:comment>
  ====================
  Constants
  ====================
  </xsl:comment><xsl:text>&#xA;</xsl:text>
<xsl:text>&#xA;</xsl:text>
<xsl:apply-templates select="constant" mode="do-constant">
<xsl:with-param name="indent" select="'  '"/>
</xsl:apply-templates>

<!-- domain elements -->
<xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:comment>
  ====================
  Domains
  ====================
  </xsl:comment><xsl:text>&#xA;</xsl:text>
<xsl:text>&#xA;</xsl:text>
<xsl:apply-templates select="domain" mode="do-domain">
<xsl:with-param name="indent" select="'  '"/>
</xsl:apply-templates>

<!-- required elementary domain definition elements added into v0.81 -->
<xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:comment> Elementary domains </xsl:comment><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">bit</xsl:attribute>
  <xsl:attribute name="type">bit</xsl:attribute>
  <xsl:attribute name="label">single bit</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">octet</xsl:attribute>
  <xsl:attribute name="type">octet</xsl:attribute>
  <xsl:attribute name="label">single octet</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">short</xsl:attribute>
  <xsl:attribute name="type">short</xsl:attribute>
  <xsl:attribute name="label">16-bit integer</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">long</xsl:attribute>
  <xsl:attribute name="type">long</xsl:attribute>
  <xsl:attribute name="label">32-bit integer</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">longlong</xsl:attribute>
  <xsl:attribute name="type">longlong</xsl:attribute>
  <xsl:attribute name="label">64-bit integer</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">shortstr</xsl:attribute>
  <xsl:attribute name="type">shortstr</xsl:attribute>
  <xsl:attribute name="label">short string</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">longstr</xsl:attribute>
  <xsl:attribute name="type">longstr</xsl:attribute>
  <xsl:attribute name="label">long string</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">timestamp</xsl:attribute>
  <xsl:attribute name="type">timestamp</xsl:attribute>
  <xsl:attribute name="label">64-bit timestamp</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:element name="domain">
  <xsl:attribute name="name">table</xsl:attribute>
  <xsl:attribute name="type">table</xsl:attribute>
  <xsl:attribute name="label">field table</xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>

<!-- class elements -->
<xsl:text>&#xA;</xsl:text>
<xsl:text>  </xsl:text><xsl:comment>
  ====================
  Classes
  ====================
  </xsl:comment><xsl:text>&#xA;</xsl:text>
<xsl:apply-templates select="class" mode="do-class">
<xsl:with-param name="indent" select="'  '"/>
</xsl:apply-templates>

</xsl:element><!-- amqp -->
<!-- <xsl:text>&#xA;</xsl:text> -->
</xsl:template>

<!-- ==========
     <constant>
     ========== -->
<xsl:template match="constant" mode="do-constant">
<xsl:param name="indent"/>
<xsl:variable name="constant" select="translate(@name, ' ', '-')"/>

<xsl:value-of select="$indent"/><xsl:element name="constant">
<xsl:attribute name="name"><xsl:value-of select="$constant"/></xsl:attribute>
<xsl:attribute name="value"><xsl:value-of select="@value"/></xsl:attribute>
<xsl:if test="@class">
<xsl:attribute name="class"><xsl:value-of select="translate(@class, ' ', '-')"/></xsl:attribute>
</xsl:if>

<!-- If there is content, place in child <doc> element -->
<xsl:if test="string-length(.) > 0">
<xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:text>  </xsl:text><xsl:element name="doc"><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:text>    </xsl:text><xsl:value-of select="normalize-space(.)"/><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:text>  </xsl:text></xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/>
</xsl:if>

</xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- ========
     <domain>
     ======== -->
<xsl:template match="domain" mode="do-domain">
<xsl:param name="indent"/>
<xsl:variable name="domain" select="translate(@name, ' ', '-')"/>

<xsl:value-of select="$indent"/><xsl:element name="domain">
<xsl:attribute name="name"><xsl:value-of select="$domain"/></xsl:attribute>
<xsl:attribute name="type"><xsl:value-of select="@type"/></xsl:attribute>
<xsl:if test="doc|assert|rule"><xsl:text>&#xA;</xsl:text></xsl:if>

<!-- doc elements -->
<xsl:apply-templates select="doc" mode="do-doc">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
</xsl:apply-templates>

<!-- assert elements -->
<xsl:apply-templates select="assert" mode="do-assert">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
</xsl:apply-templates>

<!-- rule elements -->
<xsl:apply-templates select="rule" mode="do-rule">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="$domain"/>
</xsl:apply-templates>

<xsl:if test="doc|assert|rule"><xsl:value-of select="$indent"/></xsl:if></xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- ========
     <class>
     ======== -->

<xsl:template match="class" mode="do-class">
<xsl:param name="indent"/>
<xsl:variable name="class" select="translate(@name, ' ', '-')"/>

<!-- Ignore class test - removed from 0.81 -->
<xsl:if test="not($class = 'test')">
<xsl:text>&#xA;</xsl:text><xsl:value-of select="$indent"/><xsl:comment><xsl:value-of select="concat(' == Class: ', $class, ' == ')"/></xsl:comment><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:element name="class">
<xsl:attribute name="name"><xsl:value-of select="$class"/></xsl:attribute>
<xsl:attribute name="handler"><xsl:value-of select="@handler"/></xsl:attribute>
<xsl:attribute name="index"><xsl:value-of select="@index"/></xsl:attribute>
<xsl:if test="doc|chassis|rule|field|method"><xsl:text>&#xA;</xsl:text></xsl:if>

<!-- doc elements -->
<xsl:apply-templates select="doc" mode="do-doc">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="$class"/>
</xsl:apply-templates>

<!-- chassis elements -->
<xsl:apply-templates select="chassis" mode="do-chassis">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
</xsl:apply-templates>

<!-- rule elements -->
<xsl:apply-templates select="rule" mode="do-rule">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="$class"/>
</xsl:apply-templates>

<!-- field elements -->
<xsl:apply-templates select="field" mode="do-field">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="$class"/>
</xsl:apply-templates>

<!-- method elements -->
<xsl:apply-templates select="method" mode="do-method">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="$class"/>
</xsl:apply-templates>

<xsl:if test="doc|chassis|rule|field|method"><xsl:value-of select="$indent"/></xsl:if></xsl:element><xsl:text>&#xA;</xsl:text>
</xsl:if>
</xsl:template> 

<!-- ========
     <method>
     ======== -->

<xsl:template match="method" mode="do-method">
<xsl:param name="indent"/>
<xsl:param name="label"/>
<xsl:variable name="method" select="translate(@name, ' ', '-')"/>

<xsl:text>&#xA;</xsl:text><xsl:value-of select="$indent"/><xsl:comment><xsl:value-of select="concat(' == Method: ', $label, '.', $method, ' == ')"/></xsl:comment><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:element name="method">
<xsl:attribute name="name"><xsl:value-of select="$method"/></xsl:attribute>
<xsl:if test="@synchronous"><xsl:attribute name="synchronous"><xsl:value-of select="@synchronous"/></xsl:attribute></xsl:if>
<xsl:attribute name="index"><xsl:value-of select="@index"/></xsl:attribute>
<xsl:if test="doc|chassis|response|rule|field"><xsl:text>&#xA;</xsl:text></xsl:if>

<!-- doc elements -->
<xsl:apply-templates select="doc" mode="do-doc">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="concat($label, '.', $method)"/>
</xsl:apply-templates>

<!-- chassis and response elements -->
<xsl:apply-templates select="chassis" mode="do-chassis">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
</xsl:apply-templates>
<xsl:apply-templates select="response" mode="do-response">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
</xsl:apply-templates>

<!-- rule elements -->
<xsl:apply-templates select="rule" mode="do-rule">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="concat($label, '.', $method)"/>
</xsl:apply-templates>

<!-- field elements -->
<xsl:apply-templates select="field" mode="do-field">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="concat($label, '.', $method)"/>
</xsl:apply-templates>

<xsl:if test="doc|chassis|response|rule|field"><xsl:value-of select="$indent"/></xsl:if></xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- ========
     <field>
     ======== -->

<xsl:template match="field" mode="do-field">
<xsl:param name="indent"/>
<xsl:param name="label"/>
<xsl:variable name="field" select="translate(@name, ' ', '-')"/>

<xsl:value-of select="$indent"/><xsl:element name="field">
<xsl:attribute name="name"><xsl:value-of select="$field"/></xsl:attribute>
<xsl:if test="@type">
<xsl:attribute name="domain"><xsl:value-of select="translate(@type, ' ', '-')"/></xsl:attribute>
</xsl:if>
<xsl:if test="@domain">
<xsl:attribute name="domain"><xsl:value-of select="translate(@domain, ' ', '-')"/></xsl:attribute>
</xsl:if>
<xsl:if test="doc|rule|assert"><xsl:text>&#xA;</xsl:text></xsl:if>

<!-- doc elements -->
<xsl:apply-templates select="doc" mode="do-doc">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="concat($label, '.', $field)"/>
</xsl:apply-templates>

<!-- rule elements -->
<xsl:apply-templates select="rule" mode="do-rule">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
<xsl:with-param name="label" select="concat($label, '.', $field)"/>
</xsl:apply-templates>

<!-- assert elements -->
<xsl:apply-templates select="assert" mode="do-assert">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
</xsl:apply-templates>

<xsl:if test="doc|rule|assert"><xsl:value-of select="$indent"/></xsl:if></xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- ========
     <assert>
     ======== -->
<xsl:template match="assert" mode="do-assert">
<xsl:param name="indent"/>

<xsl:value-of select="$indent"/><xsl:element name="assert">
<xsl:attribute name="check"><xsl:value-of select="@check"/></xsl:attribute>
<xsl:if test="@value"><xsl:attribute name="value"><xsl:value-of select="@value"/></xsl:attribute></xsl:if>
<xsl:if test="@rule"><xsl:attribute name="rule"><xsl:value-of select="@rule"/></xsl:attribute></xsl:if>

<xsl:apply-templates select="doc" mode="do-doc">
<xsl:with-param name="indent" select="concat($indent, '  ')"/>
</xsl:apply-templates>

</xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- ========
     <rule>
     ======== -->
<xsl:template match="rule" mode="do-rule">
<xsl:param name="indent"/>
<xsl:param name="label"/>

<xsl:value-of select="$indent"/><xsl:element name="rule">
<xsl:attribute name="name">rule_<xsl:value-of select="$label"/>_<xsl:number format="01"/></xsl:attribute>

<!-- If there is content, place in child <doc> element -->

<xsl:if test="string-length(.) > 0">
<xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:text>  </xsl:text><xsl:element name="doc"><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:text>    </xsl:text><xsl:value-of select="normalize-space(.)"/><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:text>  </xsl:text></xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/>
</xsl:if>

</xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- =========
     <chassis>
     ========= -->
<xsl:template match="chassis" mode="do-chassis">
<xsl:param name="indent"/>

<xsl:value-of select="$indent"/><xsl:element name="chassis">
<xsl:attribute name="name"><xsl:value-of select="@name"/></xsl:attribute>
<xsl:attribute name="implement"><xsl:value-of select="@implement"/></xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- ==========
     <response>
     ========== -->
<xsl:template match="response" mode="do-response">
<xsl:param name="indent"/>

<xsl:value-of select="$indent"/><xsl:element name="response">
<xsl:attribute name="name"><xsl:value-of select="@name"/></xsl:attribute>
</xsl:element><xsl:text>&#xA;</xsl:text>

</xsl:template> 

<!-- =====
     <doc>
     ===== -->
<xsl:template match="doc" mode="do-doc">
<xsl:param name="indent"/>
<xsl:param name="label"/>

<!-- Handle cases of <doc name="rule>...</doc>: turn them into <rule><doc>...</doc></rule> -->
<xsl:if test="@name = 'rule'">
<xsl:value-of select="$indent"/><xsl:element name="rule">
<xsl:if test="@test"><xsl:attribute name="name"><xsl:value-of select="@test"/></xsl:attribute></xsl:if>
<xsl:if test="not(@test)"><xsl:attribute name="name">doc_rule_<xsl:value-of select="$label"/>_<xsl:number format="01"/></xsl:attribute></xsl:if>
<xsl:text>&#xA;</xsl:text>
<xsl:value-of select="concat($indent, '  ')"/><xsl:element name="doc"><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="concat($indent, '  ')"/><xsl:text>  </xsl:text><xsl:value-of select="normalize-space(.)"/><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="concat($indent, '  ')"/></xsl:element><xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/></xsl:element><xsl:text>&#xA;</xsl:text>
</xsl:if>

<!-- Normal <doc>...</doc> elements -->
<xsl:if test="not(@name = 'rule')">
<xsl:value-of select="$indent"/><xsl:element name="doc">
<xsl:if test="@name = 'grammar'">
<xsl:attribute name="type">grammar</xsl:attribute>
<xsl:value-of select="$indent"/><xsl:text>  </xsl:text><xsl:value-of select="."/>
</xsl:if>
<xsl:if test="not(@name = 'grammar')">
<xsl:text>&#xA;</xsl:text>
<xsl:value-of select="$indent"/><xsl:text>  </xsl:text><xsl:value-of select="normalize-space(.)"/><xsl:text>&#xA;</xsl:text>
</xsl:if>
<xsl:value-of select="$indent"/></xsl:element><xsl:text>&#xA;</xsl:text>
</xsl:if>

</xsl:template> 

</xsl:stylesheet> 
