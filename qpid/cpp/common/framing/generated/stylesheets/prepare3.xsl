<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org"> 

<xsl:import href="utils.xsl"/>

<xsl:output indent="yes"/> 

<!-- final preparation of the model -->

<xsl:template match="/">
    <xsl:apply-templates mode="prepare3"/>
</xsl:template>

<xsl:template match="frames" mode="prepare3">
    <frames>
        <xsl:copy-of select="@protocol"/>
        <xsl:apply-templates mode="prepare3"/>
    </frames>
</xsl:template>

<xsl:template match="class" mode="prepare3">
    <class>
        <xsl:copy-of select="@*"/>
        <xsl:apply-templates mode="prepare3"/>
    </class>
</xsl:template>

<xsl:template match="frame" mode="prepare3">
    <xsl:element name="frame">
        <xsl:copy-of select="@*"/>
	<xsl:if test="field[@type='bit']">
            <xsl:attribute name="has-bit-field">true</xsl:attribute>
            <xsl:attribute name="bit-field-count"><xsl:value-of select="count(field[@type='bit'])"/></xsl:attribute>
        </xsl:if>
        <xsl:apply-templates mode="prepare3"/>
    </xsl:element>
</xsl:template>


<xsl:template match="field" mode="prepare3">
     <field>
         <xsl:attribute name="type"><xsl:value-of select="@type"/></xsl:attribute>
         <!-- ensure the field name is processed to be a valid java name -->
         <xsl:attribute name="name"><xsl:value-of select="amqp:field-name(@name)"/></xsl:attribute>
         <!-- add some attributes to make code generation easier -->
         <xsl:attribute name="cpp-type"><xsl:value-of select="amqp:cpp-type(@type)"/></xsl:attribute>
         <xsl:attribute name="cpp-arg-type"><xsl:value-of select="amqp:cpp-arg-type(@type)"/></xsl:attribute>
         <xsl:if test="@type='bit'">
             <xsl:attribute name="boolean-index"><xsl:number count="field[@type='bit']"/></xsl:attribute>
         </xsl:if>
     </field>
</xsl:template>

</xsl:stylesheet> 
