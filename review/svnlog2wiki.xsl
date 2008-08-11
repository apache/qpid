<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="text"></xsl:output>
    <xsl:template match="/">        
|| revision || committer || date || comment || review notes ||       
<xsl:apply-templates select="log/logentry"></xsl:apply-templates>
    </xsl:template>
    <xsl:template match="logentry">
        | [r<xsl:value-of select="@revision"/> |  <![CDATA[http://svn.apache.org/viewvc/?view=rev&revision=]]><xsl:value-of select="@revision"/> ] | <xsl:value-of select="author"/>  |  <xsl:value-of select="substring( date, 1, 10 )"/> | <xsl:value-of select="substring(msg, 1, 200)"/><xsl:if test="string-length( msg ) > 200"> ... </xsl:if> | |
    </xsl:template>
</xsl:stylesheet>
