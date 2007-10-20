<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amq="http://amq.org"> 

<xsl:import href="utils.xsl"/>

<xsl:output indent="yes"/> 

<!-- pre-process, phase 2 -->

<xsl:key name="domain-lookup" match="domain" use="name"/>

<xsl:template match="/"> 
    <xsl:apply-templates mode="prepare2" select="frames"/> 
</xsl:template> 

<xsl:template match="field[@domain]" mode="prepare2">
     <field> 
         <xsl:variable name="t1" select="key('domain-lookup', @domain)/type"/>
         <xsl:attribute name="name"><xsl:value-of select="amq:field-name(@name)"/></xsl:attribute>
         <xsl:attribute name="type"><xsl:value-of select="$t1"/></xsl:attribute>
     </field> 
</xsl:template> 

<xsl:template match="field[@type]" mode="prepare2">
     <field> 
         <xsl:attribute name="name"><xsl:value-of select="amq:field-name(@name)"/></xsl:attribute>
         <xsl:attribute name="type"><xsl:value-of select="@type"/></xsl:attribute>
     </field> 
</xsl:template> 

<xsl:template match="frames" mode="prepare2">
    <frames>
        <xsl:copy-of select="@protocol"/>
        <xsl:apply-templates mode="prepare2"/>
    </frames>
</xsl:template>

<xsl:template match="frame" mode="prepare2">
    <xsl:element name="{name()}">
        <xsl:copy-of select="@*"/>
        <xsl:apply-templates mode="prepare2" select="field"/>  
    </xsl:element>
</xsl:template>

<xsl:template match="domain" mode="prepare2"></xsl:template> 

</xsl:stylesheet> 
