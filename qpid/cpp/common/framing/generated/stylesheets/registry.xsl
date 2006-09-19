<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org"> 

<xsl:import href="java.xsl"/>

<xsl:output method="text" indent="yes" name="textFormat"/> 

<xsl:template match="/">
    <xsl:apply-templates mode="generate-registry" select="registries"/>
</xsl:template>

</xsl:stylesheet> 
