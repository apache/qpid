<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="text"></xsl:output>
    <xsl:template match="/">        
        || Key || Component(s) || Affects Version/s|| Summary || Status || Assignee || Reporter || Review Comments ||       
        <xsl:apply-templates select="rss/channel/item"></xsl:apply-templates>
    </xsl:template>
    <xsl:template match="item">
        | [<xsl:value-of select="key"/> |  <![CDATA[https://issues.apache.org/jira/browse/]]><xsl:value-of select="key"/> ] | <xsl:apply-templates select="component"/>  |  <xsl:apply-templates select="version"/> | <xsl:value-of select="title"/> | <xsl:value-of select="status"/> | <xsl:value-of select="assignee"/> | <xsl:value-of select="reporter"/> | |
    </xsl:template>
    <xsl:template match="component">
<xsl:value-of select="."/><xsl:if test="following-sibling::node()[name() = 'component']">, </xsl:if>        
    </xsl:template>
    <xsl:template match="version">
        <xsl:value-of select="."/><xsl:if test="following-sibling::node()[name() = 'version']">, </xsl:if>        
    </xsl:template>
</xsl:stylesheet>
