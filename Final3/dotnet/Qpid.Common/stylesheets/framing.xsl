<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amq="http://amq.org"> 

<xsl:import href="prepare1.xsl"/>
<xsl:import href="prepare2.xsl"/>
<xsl:import href="prepare3.xsl"/>
<xsl:import href="csharp.xsl"/>

<xsl:output indent="yes"/> 
<xsl:output method="text" indent="yes" name="textFormat"/> 

<xsl:template match="/">
    <xsl:variable name="prepare1">
        <xsl:apply-templates mode="prepare1" select="."/>
    </xsl:variable>

    <xsl:variable name="prepare2">
        <xsl:apply-templates mode="prepare2" select="$prepare1"/>
    </xsl:variable>

    <xsl:variable name="model">
        <xsl:apply-templates mode="prepare3" select="$prepare2"/>
    </xsl:variable>

    <xsl:apply-templates mode="generate-multi" select="$model"/>
    <xsl:apply-templates mode="list-registry" select="$model"/>

    <!-- dump out the intermediary files for debugging -->
    <!-- 
    <xsl:result-document href="prepare1.out">
        <xsl:copy-of select="$prepare1"/> 
    </xsl:result-document>

    <xsl:result-document href="prepare2.out">
        <xsl:copy-of select="$prepare2"/> 
    </xsl:result-document>

    <xsl:result-document href="model.out">
        <xsl:copy-of select="$model"/> 
    </xsl:result-document>
    -->
</xsl:template>

</xsl:stylesheet> 
