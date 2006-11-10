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

<!-- This file contains functions that are used in the generation of the java classes for framing -->

<!-- retrieve the java type of a given amq type -->
<xsl:function name="amqp:cpp-type">
    <xsl:param name="t"/>
    <xsl:choose>
	 <xsl:when test="$t='octet'">u_int8_t</xsl:when> 		 		 
	 <xsl:when test="$t='short'">u_int16_t</xsl:when> 		 		 
	 <xsl:when test="$t='shortstr'">string</xsl:when> 		 		 
	 <xsl:when test="$t='longstr'">string</xsl:when> 		 		 
	 <xsl:when test="$t='bit'">bool</xsl:when> 		 		 
	 <xsl:when test="$t='long'">u_int32_t</xsl:when> 		 		 
	 <xsl:when test="$t='longlong'">u_int64_t</xsl:when> 		 		 
	 <xsl:when test="$t='table'">FieldTable</xsl:when> 		 		 
         <xsl:otherwise>Object /*WARNING: undefined type*/</xsl:otherwise>
    </xsl:choose>
</xsl:function>
<xsl:function name="amqp:cpp-arg-type">
    <xsl:param name="t"/>
    <xsl:choose>
	 <xsl:when test="$t='octet'">u_int8_t</xsl:when> 		 		 
	 <xsl:when test="$t='short'">u_int16_t</xsl:when> 		 		 
	 <xsl:when test="$t='shortstr'">const string&amp;</xsl:when> 		 		 
	 <xsl:when test="$t='longstr'">const string&amp;</xsl:when> 		 		 
	 <xsl:when test="$t='bit'">bool</xsl:when> 		 		 
	 <xsl:when test="$t='long'">u_int32_t</xsl:when> 		 		 
	 <xsl:when test="$t='longlong'">u_int64_t</xsl:when> 		 		 
	 <xsl:when test="$t='table'">const FieldTable&amp;</xsl:when> 		 		 
         <xsl:otherwise>Object /*WARNING: undefined type*/</xsl:otherwise>
    </xsl:choose>
</xsl:function>

<!-- retrieve the code to get the field size of a given amq type -->
<xsl:function name="amqp:field-length">
    <xsl:param name="f"/>
    <xsl:choose>
        <xsl:when test="$f/@type='bit' and $f/@boolean-index=1">
            <xsl:value-of select="concat('1 /*', $f/@name, '*/')"/>
        </xsl:when>
        <xsl:when test="$f/@type='bit' and $f/@boolean-index &gt; 1">
            <xsl:value-of select="concat('0 /*', $f/@name, '*/')"/>
        </xsl:when>
        <xsl:when test="$f/@type='char'">
            <xsl:value-of select="concat('1 /*', $f/@name, '*/')"/>
        </xsl:when>
        <xsl:when test="$f/@type='octet'">
            <xsl:value-of select="concat('1 /*', $f/@name, '*/')"/>
        </xsl:when>
	<xsl:when test="$f/@type='short'">
            <xsl:value-of select="concat('2 /*', $f/@name, '*/')"/>
        </xsl:when>
        <xsl:when test="$f/@type='long'">
            <xsl:value-of select="concat('4 /*', $f/@name, '*/')"/>
        </xsl:when>
        <xsl:when test="$f/@type='longlong'">
            <xsl:value-of select="concat('8 /*', $f/@name, '*/')"/>
        </xsl:when>
	<xsl:when test="$f/@type='shortstr'">
            <xsl:value-of select="concat('1 + ', $f/@name, '.length()')"/>
        </xsl:when> 		 		 
	<xsl:when test="$f/@type='longstr'">
            <xsl:value-of select="concat('4 + ', $f/@name, '.length()')"/>
        </xsl:when> 		 		 
	<xsl:when test="$f/@type='table'">
            <xsl:value-of select="concat($f/@name, '.size()')"/>
        </xsl:when> 		 		 
        <xsl:otherwise><xsl:text>/* WARNING: COULD NOT DETERMINE FIELD SIZE */</xsl:text></xsl:otherwise>
    </xsl:choose>    
</xsl:function>

<!-- retrieve the code to encode a field of a given amq type -->
<!-- Note:
     This method will not provide an encoder for a bit field. 
     Bit fields should be encoded together separately. -->

<xsl:function name="amqp:encoder">
    <xsl:param name="f"/>
    <xsl:choose>
        <xsl:when test="$f/@type='octet'">
            <xsl:value-of select="concat('buffer.putOctet(', $f/@name, ')')"/>
        </xsl:when>
	<xsl:when test="$f/@type='short'">
            <xsl:value-of select="concat('buffer.putShort(', $f/@name, ')')"/>
        </xsl:when>
        <xsl:when test="$f/@type='long'">
            <xsl:value-of select="concat('buffer.putLong(', $f/@name, ')')"/>
        </xsl:when>
        <xsl:when test="$f/@type='longlong'">
            <xsl:value-of select="concat('buffer.putLongLong(', $f/@name, ')')"/>
        </xsl:when>
	<xsl:when test="$f/@type='shortstr'">
            <xsl:value-of select="concat('buffer.putShortString(', $f/@name, ')')"/>
        </xsl:when> 		 		 
	<xsl:when test="$f/@type='longstr'">
            <xsl:value-of select="concat('buffer.putLongString(', $f/@name, ')')"/>
        </xsl:when> 		 		 
	<xsl:when test="$f/@type='table'">
            <xsl:value-of select="concat('buffer.putFieldTable(', $f/@name, ')')"/>
        </xsl:when> 		 		 
        <xsl:otherwise><xsl:text>/* WARNING: COULD NOT DETERMINE ENCODER */</xsl:text></xsl:otherwise>
    </xsl:choose>    
</xsl:function>

<!-- retrieve the code to decode a field of a given amq type -->
<xsl:function name="amqp:decoder">
    <xsl:param name="f"/>
    <xsl:choose>
        <xsl:when test="$f/@type='bit'">
            <xsl:value-of select="concat($f/@name, ' = (1 &lt;&lt; (', $f/@boolean-index, ' - 1)) &amp; flags;')"/>
        </xsl:when>
        <xsl:when test="$f/@type='octet'">
            <xsl:value-of select="concat($f/@name, ' = buffer.getOctet()')"/>
        </xsl:when>
        <xsl:when test="$f/@type='short'">
            <xsl:value-of select="concat($f/@name, ' = buffer.getShort()')"/>
        </xsl:when>
        <xsl:when test="$f/@type='long'">
            <xsl:value-of select="concat($f/@name, ' = buffer.getLong()')"/>
        </xsl:when>
        <xsl:when test="$f/@type='longlong'">
            <xsl:value-of select="concat($f/@name, ' = buffer.getLongLong()')"/>
        </xsl:when>
        <xsl:when test="$f/@type='shortstr'">
            <xsl:value-of select="concat('buffer.getShortString(', $f/@name), ')'"/>
        </xsl:when>
        <xsl:when test="$f/@type='longstr'">
            <xsl:value-of select="concat('buffer.getLongString(', $f/@name), ')'"/>
        </xsl:when>
        <xsl:when test="$f/@type='table'">
            <xsl:value-of select="concat('buffer.getFieldTable(', $f/@name, ')')"/>
        </xsl:when>
        <xsl:otherwise><xsl:text>/* WARNING: COULD NOT DETERMINE DECODER */</xsl:text></xsl:otherwise>
    </xsl:choose>    
</xsl:function>

<!-- create the class name for a frame, based on class and method (passed in) -->
<xsl:function name="amqp:class-name">
    <xsl:param name="class"/>
    <xsl:param name="method"/>
    <xsl:value-of select="concat(amqp:upper-first($class),amqp:upper-first(amqp:field-name($method)), 'Body')"/>
</xsl:function>

<!-- create the class name for a frame, based on class and method (passed in) -->
<xsl:function name="amqp:method-name">
    <xsl:param name="class"/>
    <xsl:param name="method"/>
    <xsl:value-of select="concat(translate($class, '- ', '__'), '_', translate($method, '- ', '__'))"/>
</xsl:function>

<!-- get a valid field name, processing spaces and '-'s where appropriate -->
<xsl:function name="amqp:field-name">
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

<!-- convert the first character of the input to upper-case -->
<xsl:function name="amqp:upper-first">
    <xsl:param name="in"/>
    <xsl:value-of select="concat(upper-case(substring($in, 1, 1)), substring($in, 2))"/>
</xsl:function>


<xsl:function name="amqp:keyword-check">
    <xsl:param name="in"/>
    <xsl:choose>
        <xsl:when test="contains($in, 'delete')">
            <xsl:value-of select="concat($in, '_')"/>
        </xsl:when>
        <xsl:when test="contains($in, 'string')">
            <xsl:value-of select="concat($in, '_')"/>
        </xsl:when>
        <xsl:when test="contains($in, 'return')">
            <xsl:value-of select="concat($in, '_')"/>
        </xsl:when>
        <xsl:otherwise>
            <xsl:value-of select="$in"/>
        </xsl:otherwise>
    </xsl:choose>
</xsl:function>

</xsl:stylesheet>
