<?xml version="1.0" encoding="UTF-8"?>
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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output indent="no"/>
    <xsl:template match="node()[name()='amqp']">

<html>
    <head>
        <title>AMQP <xsl:call-template name="initCap"><xsl:with-param name="input" select="@name"/></xsl:call-template></title>
        <style type="text/css">
            body {
            margin: 2em;
            padding: 1em;
            font-family: "DejaVu LGC Sans", "Bitstream Vera Sans", arial, helvetica, sans-serif;
            }
            
            h2 {
            font-size: 1.25em;
            color: #339;
            }
            
            h2 a {
            color: #339;
            }
            
            h3 {
            font-size: 1em;
            position: relative;
            left: -0.5em;
            }
            
            h4 {
            font-size: 1em;
            position: relative;
            left: -0.5em;
            }
            
            h1 a, h3 a, h4 a {
            color: #000;
            }
            
            a {
            text-decoration: none;
            color: #66f;
            }
            
            a.anchor {
            color: #000;
            }
            
            a.toc {
            float: right;
            }
            
            table {
            border-collapse: collapse;
            margin: 1em 1em;
            }
            
            dt {
            font-weight: bold;
            }
            
            dt:after {
            content: ":";
            }
            
            div.toc {
            border: 1px solid #ccc;
            padding: 1em;
            }
            
            table.toc {
            margin: 0;
            }
            
            table.pre {
            background: #eee;
            border: 1px solid #ccc;
            margin-left: auto;
            margin-right: auto;
            }
            
            table.pre td {
            font-family: Courier, monospace;
            white-space: pre;
            padding: 0em 2em;
            }
            
            table.definition {
            width: 100%;
            }
            
            table.signature {
            background: #eee;
            border: 1px solid #ccc;
            width: 100%;
            }
            
            table.signature td {
            font-family: monospace;
            white-space: pre;
            padding: 1em;
            }
            
            table.composite {
            width: 100%;
            }
            
            td.field {
            padding: 0.5em 2em;
            }
            
            div.section {
            padding: 0 0 0 1em;
            }
            
            div.doc {
            padding: 0 0 1em 0;
            }
            
            table.composite div.doc {
            padding: 0;
            }
            
            table.error {
            width: 100%;
            margin: 0;
            }
            
            table.error tr:last-child td {
            padding: 0 0 0 1em;
            }
            
            .todo:before {
            content: "TODO: ";
            font-weight: bold;
            color: red;
            }
            
            .todo {
            font-variant: small-caps;
            font-weight: bold;
            color: red;
            }
        </style>        
    </head>
    <body>
        <h1>AMQP <xsl:call-template name="initCap"><xsl:with-param name="input" select="@name"/></xsl:call-template></h1>
        
        <xsl:if test="descendant-or-self::node()[name()='section']"><xsl:call-template name="toc"/></xsl:if>
        <xsl:apply-templates mode="spec" select="."/>
    </body>
</html>
    </xsl:template>

    <!-- Table of Contents -->

    <xsl:template name="toc">
        <h2><a name="toc">Table of Contents</a></h2>
        <div class="toc">
            <table class="toc" summary="Table of Contents">
                <xsl:apply-templates mode="toc" select="."/>
            </table>
        </div>        
    </xsl:template>

    <xsl:template mode="toc" match="node()[name()='section']">
        <xsl:variable name="title"><xsl:choose>
            <xsl:when test="@title"><xsl:value-of select="@title"/></xsl:when>
            <xsl:otherwise><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="translate(@name,'-',' ')"/></xsl:call-template></xsl:otherwise>
        </xsl:choose>
        </xsl:variable>
        <xsl:variable name="ref">#section-<xsl:value-of select="@name"/></xsl:variable>
        <xsl:variable name="section-number"><xsl:value-of select="count(preceding-sibling::node()[name()='section'])+1"/></xsl:variable>
        <tr><td><xsl:value-of select="$section-number"/><xsl:text> </xsl:text><xsl:element name="a"><xsl:attribute name="href"><xsl:value-of select="$ref"/></xsl:attribute><xsl:value-of select="$title"/></xsl:element></td><td><xsl:value-of select="@label"/></td></tr>
        <xsl:apply-templates mode="toc" select="node()[name()='type' or name()='definition' or ( name()='doc' and attribute::node()[name()='title']!='')]"/>
    </xsl:template>

    <xsl:template mode="toc" match="node()[name()='doc' and  attribute::node()[name()='title']]">
        <xsl:variable name="ref">#doc-<xsl:choose><xsl:when test="@name"><xsl:value-of select="@name"/></xsl:when><xsl:otherwise><xsl:value-of select="generate-id(.)"/></xsl:otherwise></xsl:choose></xsl:variable>
        <xsl:variable name="section-number"><xsl:apply-templates mode="doc-number" select="."/></xsl:variable>
        <tr><td><xsl:text>&#160;&#160;&#160;&#160;&#160;&#160;</xsl:text><xsl:value-of select="$section-number"/><xsl:text> </xsl:text><xsl:element name="a"><xsl:attribute name="href"><xsl:value-of select="$ref"/></xsl:attribute><xsl:value-of select="@title"/></xsl:element></td><td><xsl:value-of select="@label"/></td></tr>        
    </xsl:template>
    
    <xsl:template mode="toc" match="node()[name()='type' or name()='definition']">
        <xsl:variable name="ref">#<xsl:value-of select="name()"/>-<xsl:value-of select="@name"/></xsl:variable>        
        <xsl:variable name="section-number"><xsl:apply-templates mode="type-number" select="."/></xsl:variable>        
        <tr><td><xsl:text>&#160;&#160;&#160;&#160;&#160;&#160;</xsl:text><xsl:if test="contains(substring-after($section-number,'.'),'.')"><xsl:text>&#160;&#160;&#160;&#160;&#160;&#160;</xsl:text></xsl:if><xsl:value-of select="$section-number"/><xsl:text> </xsl:text><xsl:element name="a"><xsl:attribute name="href"><xsl:value-of select="$ref"/></xsl:attribute><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="translate(@name,'-',' ')"/></xsl:call-template></xsl:element></td><td><xsl:value-of select="@label"/></td></tr>        
    </xsl:template>
    
    <!-- Sections -->
    
    <xsl:template match="node()[name()='section']" mode="spec">
        <xsl:variable name="title"><xsl:choose>
            <xsl:when test="@title"><xsl:value-of select="@title"/></xsl:when>
            <xsl:otherwise><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="translate(@name,'-',' ')"/></xsl:call-template></xsl:otherwise>
        </xsl:choose>
        </xsl:variable>        
        <h2><a class="toc" href="#toc"><xsl:text>&#8592;</xsl:text></a> <xsl:apply-templates mode="section-number" select="."/><xsl:text> </xsl:text><xsl:element name="a"><xsl:attribute name="name">section-<xsl:value-of select="@name"/></xsl:attribute><xsl:value-of select="$title"/></xsl:element></h2>
        <div class="section">
            <xsl:apply-templates mode="spec" select="*"/>
        </div>
    </xsl:template>
    
    <!-- docs -->
    <xsl:template match="node()[name()='doc']" mode="spec">
        <xsl:choose>
          <xsl:when test="ancestor::node()[name() = 'doc']">
        <xsl:if test="@title">
            <h4><a class="toc" href="#toc"><xsl:text>&#8592;</xsl:text></a><xsl:element name="a"><xsl:attribute name="name">doc-<xsl:choose><xsl:when test="@name"><xsl:value-of select="@name"/></xsl:when><xsl:otherwise><xsl:value-of select="generate-id(.)"/></xsl:otherwise></xsl:choose></xsl:attribute><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="translate(@title,'-',' ')"></xsl:with-param></xsl:call-template></xsl:element></h4>
        </xsl:if>
            <xsl:apply-templates mode="spec" select="*|comment()"/>            
          </xsl:when>
          <xsl:otherwise>
        <xsl:if test="@title">
            <h3><a class="toc" href="#toc"><xsl:text>&#8592;</xsl:text></a> <xsl:apply-templates mode="doc-number" select="."/><xsl:text> </xsl:text><xsl:element name="a"><xsl:attribute name="name">doc-<xsl:choose><xsl:when test="@name"><xsl:value-of select="@name"/></xsl:when><xsl:otherwise><xsl:value-of select="generate-id(.)"/></xsl:otherwise></xsl:choose></xsl:attribute><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="translate(@title,'-',' ')"></xsl:with-param></xsl:call-template></xsl:element></h3>
        </xsl:if>
        <div class="doc">
            <xsl:apply-templates mode="spec" select="*|comment()"/>            
        </div>
          </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- picture -->
    
    <xsl:template match="node()[name()='picture']" mode="spec">
        <xsl:element name="table"><xsl:attribute name="class">pre</xsl:attribute><xsl:if test="@title"><xsl:attribute name="summary"><xsl:value-of select="@title"/></xsl:attribute></xsl:if>
            <xsl:if test="@title"><caption><xsl:value-of select="@title"/></caption></xsl:if>
<tr><td>
<xsl:apply-templates select="*|text()" mode="spec"/>    
</td></tr></xsl:element>
    </xsl:template>

    <!-- xref -->
<xsl:template match="node()[name()='xref']" mode="spec">
    <xsl:variable name="ref" select="@name"/>
    <xsl:choose>
        <!-- section xref -->
        <xsl:when test="@type='section' or ancestor-or-self::node()/descendant-or-self::node()[name()='section' and @name=$ref]">
            <xsl:element name="a"><xsl:attribute name="href">#section-<xsl:value-of select="@name"/></xsl:attribute><xsl:value-of select="@name"/></xsl:element>
        </xsl:when>
        <!-- type xref -->
        <xsl:when test="@type='type'  or ancestor-or-self::node()/descendant-or-self::node()[name()='type' and @name=$ref]">
            <xsl:element name="a"><xsl:attribute name="href">#type-<xsl:value-of select="@name"/></xsl:attribute><xsl:value-of select="@name"/></xsl:element>
        </xsl:when>
        <!-- doc xref -->
        <xsl:when test="@type='doc'  or ancestor-or-self::node()/descendant-or-self::node()[name()='doc' and @name=$ref]">
            <xsl:element name="a"><xsl:attribute name="href">#doc-<xsl:value-of select="@name"/></xsl:attribute><xsl:value-of select="@name"/></xsl:element>
        </xsl:when>
        <xsl:when test="@type='amqp'">
            <xsl:element name="a"><xsl:attribute name="href"><xsl:value-of select="@name"/>.xml</xsl:attribute><xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template></xsl:element>
        </xsl:when>
        <xsl:otherwise><xsl:value-of select="@name"/></xsl:otherwise>
    </xsl:choose>
    
           
    
</xsl:template>

    <!-- todo -->
    <xsl:template match="node()[name()='todo']" mode="spec"><span class="todo"><xsl:apply-templates select="*|text()" mode="spec"/></span></xsl:template>
    
    <!-- primitive type -->
    <xsl:template match="node()[name()='type' and @class='primitive']" mode="spec">
        <h3><a class="toc" href="#toc"><xsl:text>&#8592;</xsl:text></a> <xsl:apply-templates mode="type-number" select="."/><xsl:text> </xsl:text> <xsl:element name="a"><xsl:attribute name="name">type-<xsl:value-of select="@name"/></xsl:attribute><xsl:call-template name="initCap"><xsl:with-param name="input" select="@name"/></xsl:call-template></xsl:element></h3>
        <xsl:apply-templates select="." mode="type-signature"/>
        <xsl:element name="table"><xsl:attribute name="class">signature</xsl:attribute><xsl:attribute name="summary"><xsl:value-of select="@label"/></xsl:attribute>
            <tr><td><b><xsl:value-of select="@name"/></b>: <xsl:value-of select="@label"/></td></tr>        
        </xsl:element>
        
        <b>Encodings:</b>
        <xsl:element name="table"><xsl:attribute name="class">composite</xsl:attribute><xsl:attribute name="summary"><xsl:value-of select="@label"/></xsl:attribute>
        <xsl:apply-templates select="node()[name()='encoding']" mode="spec"/>
        </xsl:element>
    </xsl:template>        
    
    
    
    <!-- composite type -->
    <xsl:template match="node()[name()='type' and @class='composite']" mode="spec">
        <h3><a class="toc" href="#toc"><xsl:text>&#8592;</xsl:text></a> <xsl:apply-templates mode="type-number" select="."/><xsl:text> </xsl:text> <xsl:element name="a"><xsl:attribute name="name">type-<xsl:value-of select="@name"/></xsl:attribute><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="translate(@name,'-',' ')"></xsl:with-param></xsl:call-template></xsl:element></h3>
        <xsl:apply-templates select="." mode="type-signature"/>        
        <xsl:apply-templates select="node()[name()='doc']" mode="spec"/>
        <xsl:element name="table"><xsl:attribute name="class">composite</xsl:attribute><xsl:attribute name="summary"><xsl:value-of select="@name"/> fields</xsl:attribute>
        <xsl:for-each select="node()[name()='field']">    
            <tr><td><b><xsl:value-of select="@name"/></b></td><td><i><xsl:value-of select="@label"/></i></td><td><xsl:choose>
                <xsl:when test="@mandatory">mandatory</xsl:when>
                <xsl:otherwise>optional</xsl:otherwise>
            </xsl:choose><xsl:text> </xsl:text><xsl:call-template name="genTypeXref"><xsl:with-param name="type" select="@type"/></xsl:call-template><xsl:if test="@multiple">[]</xsl:if></td></tr>            
            <tr><td class="field" colspan="2"><xsl:apply-templates select="node()[name()='doc' or name()='error']" mode="spec"/></td></tr>
        </xsl:for-each>
        </xsl:element>
    </xsl:template>
    
    <!-- restricted type -->
    <xsl:template match="node()[name()='type' and @class='restricted']" mode="spec">
        <xsl:variable name="name"><xsl:value-of select="@name"/></xsl:variable>
        <h3><a class="toc" href="#toc"><xsl:text>&#8592;</xsl:text></a> <xsl:apply-templates mode="type-number" select="."/><xsl:text> </xsl:text> <xsl:element name="a"><xsl:attribute name="name">type-<xsl:value-of select="@name"/></xsl:attribute><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="translate(@name,'-',' ')"></xsl:with-param></xsl:call-template></xsl:element></h3>
        <xsl:apply-templates select="." mode="type-signature"/>
        <xsl:apply-templates select="node()[name()='doc']" mode="spec"/>
        <xsl:element name="table"><xsl:attribute name="class">composite</xsl:attribute><xsl:attribute name="summary">possible values</xsl:attribute>
            <xsl:for-each select="node()[name()='choice']">
                <tr><td><b><xsl:element name="a"><xsl:attribute name="class">anchor</xsl:attribute><xsl:attribute name="name">choice-<xsl:value-of select="$name"/>-<xsl:value-of select="@name"/></xsl:attribute><xsl:value-of select="@name"/></xsl:element></b></td><td></td><td><xsl:value-of select="@value"/></td></tr>
            <tr><td class="field" colspan="2"><xsl:apply-templates select="node()[name()='doc' or name()='error']" mode="spec"/></td></tr>
            </xsl:for-each>
        </xsl:element>    
    </xsl:template>

    <!-- type signature -->
    <xsl:template match="node()[name()='type']" mode="type-signature">
        <table class="signature" summary="@(label)"><tr>
<td>&#60;type<xsl:if test="@name"><xsl:text> </xsl:text>name="<xsl:value-of select="@name"/>"</xsl:if><xsl:if test="@class"><xsl:text> </xsl:text>class="<xsl:value-of select="@class"/>"</xsl:if><xsl:if test="@source"><xsl:text> </xsl:text>source="<xsl:value-of select="@source"/>"</xsl:if><xsl:if test="@provides"><xsl:text> </xsl:text>provides="<xsl:value-of select="@provides"/>"</xsl:if><xsl:if test="not( node()[name() = 'field' or name() = 'choice' or name() = 'descriptor'])">/</xsl:if>&#62;<xsl:if test="node()[name() = 'field' or name() = 'choice' or name() = 'descriptor']">
<xsl:apply-templates select="node()[name()='field' or name()='choice' or name()='descriptor']" mode="type-signature"/><xsl:text>
&#60;/type&#62;</xsl:text>    
</xsl:if>
        </td></tr></table>
    </xsl:template>
    
    <xsl:template match="node()[name()='descriptor']" mode="type-signature">
<xsl:text>
    </xsl:text>&#60;descriptor<xsl:if test="@name"><xsl:text> </xsl:text>name="<xsl:value-of select="@name"/>"</xsl:if><xsl:if test="@code"><xsl:text> </xsl:text>code="<xsl:value-of select="@code"/>"</xsl:if><xsl:text>/&#62;</xsl:text>        
    </xsl:template>
    
    <xsl:template match="node()[name()='choice']" mode="type-signature">
<xsl:text>
    </xsl:text>&#60;choice<xsl:for-each select="attribute::node()[name() != 'label']"><xsl:text> </xsl:text><xsl:value-of select="name()"/>="<xsl:value-of select="."/>"</xsl:for-each><xsl:text>/&#62;</xsl:text>        
    </xsl:template>
    
    <xsl:template match="node()[name()='field']" mode="type-signature">
<xsl:text>
    </xsl:text>&#60;field<xsl:for-each select="attribute::node()[name() != 'label']"><xsl:text> </xsl:text><xsl:value-of select="name()"/>="<xsl:value-of select="."/>"</xsl:for-each><xsl:if test="not( node()[name()='error'] )"><xsl:text>/</xsl:text></xsl:if>&#62;<xsl:if test="node()[name()='error']">
<xsl:apply-templates mode="type-signature" select="node()[name()='error']"/><xsl:text>
    &#60;/field&#62;</xsl:text></xsl:if>        
    </xsl:template>

    <xsl:template match="node()[name()='error']" mode="type-signature">
<xsl:text>
        </xsl:text>&#60;error<xsl:for-each select="attribute::node()[name() != 'label']"><xsl:text> </xsl:text><xsl:value-of select="name()"/>="<xsl:value-of select="."/>"</xsl:for-each><xsl:text>/&#62;</xsl:text>        
    </xsl:template>
    

    <!-- encoding -->    
    <xsl:template match="node()[name()='encoding']" mode="spec">
        <tr><td><b><xsl:value-of select="@name"/></b></td><td><i><xsl:value-of select="@label"/></i></td><td>code <xsl:value-of select="@code"/>: <xsl:choose>
            <xsl:when test="@category='fixed'">fixed-width, <xsl:value-of select="@width"/> byte value</xsl:when>
            <xsl:otherwise>variable-width, <xsl:value-of select="@width"/> byte size</xsl:otherwise>
        </xsl:choose></td></tr>
        <tr><td class="field" colspan="3"><xsl:apply-templates select="node()[name()='doc']" mode="spec"/></td></tr>
        
    </xsl:template>    
    

    <!-- TypeListTable comment -->
    <xsl:template match="comment()[self::comment()='TypeListTable']" mode="spec">
        <table class="" summary="Primitive Types">
            <xsl:for-each select="//descendant-or-self::node()[name()='type']">
                <tr><td><xsl:element name="a"><xsl:attribute name="href">#type-<xsl:value-of select="@name"/></xsl:attribute><xsl:value-of select="@name"/></xsl:element></td><td><xsl:value-of select="@label"/></td></tr>
            </xsl:for-each>
        </table>
    </xsl:template>

    <!-- EncodingListTable comment -->
    <xsl:template match="comment()[self::comment()='EncodingListTable']" mode="spec">
        <table class="" summary="Primitive Type Encodings">
            <tr><th>Type</th><th>Encoding</th><th>Code</th><th>Category</th><th>Description</th></tr>
            <xsl:for-each select="//descendant-or-self::node()[name()='type']">
                <xsl:variable name="type" select="@name"/>
                <xsl:for-each select="node()[name()='encoding']">
                    <tr>
                        <td><xsl:element name="a"><xsl:attribute name="href">#type-<xsl:value-of select="$type"/></xsl:attribute><xsl:value-of select="$type"/></xsl:element></td>
                        <td><xsl:value-of select="@name"/></td>
                        <td><xsl:value-of select="@code"/></td>
                        <td><xsl:value-of select="@category"/>/<xsl:value-of select="@width"/></td>
                        <td><xsl:value-of select="@label"/></td>
                    </tr>
                </xsl:for-each>                
            </xsl:for-each>
        </table>
    </xsl:template>
    

    <!-- text -->

    <xsl:template match="text()" mode="spec"><xsl:value-of select="."/></xsl:template>

    <!-- error -->
    
    <xsl:template match="node()[name()='error']" mode="spec">
        <table class="error" summary="">
            <tr>
                <td><b><xsl:value-of select="@name"/> error:</b> </td>
                
                <td align="right"><xsl:element name="a"><xsl:attribute name="href"><xsl:call-template name="genErrorXref"><xsl:with-param name="type" select="@value"/></xsl:call-template></xsl:attribute><xsl:value-of select="@value"/></xsl:element></td>
            </tr>
            <tr><td colspan="2"><xsl:apply-templates select="node()[name()='doc']" mode="spec"/></td></tr>
        </table>        
    </xsl:template>
    
    <!-- definition -->

    <xsl:template mode="spec" match="node()[name()='definition']">
        <h3><a class="toc" href="#toc"><xsl:text>&#8592;</xsl:text></a> <xsl:apply-templates mode="type-number" select="."/><xsl:text> </xsl:text> <xsl:element name="a"><xsl:attribute name="name">definition-<xsl:value-of select="@name"/></xsl:attribute><xsl:call-template name="initCap"><xsl:with-param name="input" select="@name"/></xsl:call-template></xsl:element></h3>
        
        <xsl:element name="table"><xsl:attribute name="class">signature</xsl:attribute><xsl:attribute name="summary"><xsl:value-of select="@label"/></xsl:attribute>
            <tr>
                <td><xsl:value-of select="@name"/>: <xsl:value-of select="@label"/></td>
                <td align="right"><xsl:value-of select="@value"/></td>
            </tr>
        </xsl:element>
        <xsl:apply-templates mode="spec" select="node()[name()='doc']"/>
    </xsl:template>

    <!-- pass through tags -->

    <xsl:template match="node()[name() = 'p']" mode="spec"> 
        <xsl:element name="p"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'i']" mode="spec"> 
        <xsl:element name="i"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'term']" mode="spec"> 
        <xsl:element name="i"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'b']" mode="spec"> 
        <xsl:element name="b"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'ol']" mode="spec"> 
        <xsl:element name="ol"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'ul']" mode="spec"> 
        <xsl:element name="ul"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'li']" mode="spec"> 
        <xsl:element name="li"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'dl']" mode="spec"> 
        <xsl:element name="dl"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'dt']" mode="spec"> 
        <xsl:element name="dt"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    <xsl:template match="node()[name() = 'dd']" mode="spec"> 
        <xsl:element name="dd"><xsl:if test="@title"><xsl:attribute name="title"><xsl:value-of select="@title"/></xsl:attribute></xsl:if><xsl:apply-templates mode="spec" select="*|text()"/></xsl:element>                
    </xsl:template>
    
    

    <!-- Document specific utilities -->

    <xsl:template mode="section-number" match="node()[name()='section']"><xsl:value-of select="count(preceding-sibling::node()[name()='section'])+1"/></xsl:template>
    <xsl:template mode="doc-number" match="node()[name()='doc']"><xsl:apply-templates mode="section-number" select="ancestor::node()[name()='section']"/>.<xsl:value-of select="1+count(preceding-sibling::node()[name()='doc' and attribute::title])"/></xsl:template>    
    <xsl:template mode="type-number" match="node()[name()='type' or name()='definition']">
        <xsl:choose>
            <xsl:when test="count(preceding-sibling::node()[name()='doc' and attribute::title])>0">
                <xsl:variable name="doc-node" select="preceding-sibling::node()[name()='doc' and attribute::title]"/>
                <xsl:variable name="offset" select="count($doc-node/preceding-sibling::node()[name()='type' or name()='definition'])"></xsl:variable>
                <xsl:apply-templates mode="section-number" select="ancestor::node()[name()='section']"/>.<xsl:value-of select="count(preceding-sibling::node()[name()='doc' and attribute::title])"/>.<xsl:value-of select="1+count(preceding-sibling::node()[name()='type' or name()='definition'])-$offset"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates mode="section-number" select="ancestor::node()[name()='section']"/>.<xsl:value-of select="1+count(preceding-sibling::node()[name()='type' or name()='definition'])"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    <xsl:template name="genTypeXref">
        <xsl:param name="type"/>
        <xsl:choose>
            <xsl:when test="$type='*'">*</xsl:when>
            <xsl:otherwise><xsl:element name="a"><xsl:attribute name="href"><xsl:choose>                
                <xsl:when test="/ancestor-or-self::node()/descendant-or-self::node()[name()='type' and @name=$type]">#type-<xsl:value-of select="$type"/></xsl:when>
            <xsl:otherwise>
                <xsl:for-each select="document('index.xml')/descendant-or-self::node()[name()='xref' and @type='amqp']">
                    <xsl:variable name="docname"><xsl:value-of select="@name"/>.xml</xsl:variable>
                    <xsl:if test="document($docname)/descendant-or-self::node()[name()='type' and @name=$type]"><xsl:value-of select="$docname"/>#type-<xsl:value-of select="$type"/></xsl:if>
                </xsl:for-each>
            </xsl:otherwise>
            </xsl:choose></xsl:attribute><xsl:value-of select="$type"/></xsl:element>
            </xsl:otherwise>
        </xsl:choose>        
    </xsl:template>
    
    <xsl:template name="genErrorXref">
        <xsl:param name="type"/>
        <xsl:choose>
            <xsl:when test="/ancestor-or-self::node()/descendant-or-self::node()[name()='type' and @provides='error']/node()[name()='choice' and @name=$type]">#choice-<xsl:value-of select="/ancestor-or-self::node()/descendant-or-self::node()[name()='type' and @provides='error' and child::node()[name()='choice' and @name=$type]]/@name"/>-<xsl:value-of select="$type"/></xsl:when>
            <xsl:otherwise>
                <xsl:for-each select="document('index.xml')/descendant-or-self::node()[name()='xref' and @type='amqp']">
                    <xsl:variable name="docname"><xsl:value-of select="@name"/>.xml</xsl:variable>
                    <xsl:if test="document($docname)/descendant-or-self::node()[name()='type' and @provides='error']/node()[name()='choice' and @name=$type]">
                        #choice-<xsl:value-of select="document($docname)/descendant-or-self::node()[name()='type' and @provides='error' and child::node()[name()='choice' and @name=$type]]/@name"/>-<xsl:value-of select="$type"/>                        
                    </xsl:if>
                </xsl:for-each>
            </xsl:otherwise>
        </xsl:choose>            
    </xsl:template>
    <!-- General Utilities -->
    
    <xsl:template name="toUpper"><xsl:param name="input"/><xsl:value-of select="translate($input,'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"></xsl:value-of></xsl:template>
    <xsl:template name="toLower"><xsl:param name="input"/><xsl:value-of select="translate($input,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"></xsl:value-of></xsl:template>
    <xsl:template name="initCap"><xsl:param name="input"/><xsl:call-template name="toUpper"><xsl:with-param name="input" select="substring($input,1,1)"/></xsl:call-template><xsl:value-of select="substring($input,2)"/></xsl:template>
    <xsl:template name="initCapWords">
        <xsl:param name="input"/>
        <xsl:choose>
            <xsl:when test="contains($input,' ')">
                <xsl:call-template name="initCap"><xsl:with-param name="input" select="substring-before($input, ' ')"/></xsl:call-template><xsl:text> </xsl:text><xsl:call-template name="initCapWords"><xsl:with-param name="input" select="substring-after($input, ' ')"/></xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:call-template name="initCap"><xsl:with-param name="input" select="$input"/></xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
</xsl:stylesheet>
