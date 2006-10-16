<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org"> 

  <xsl:import href="code_utils.xsl"/>
  <xsl:output method="text" indent="yes" name="textFormat"/> 

  <xsl:template match="/">
    <xsl:apply-templates select="amqp" mode="domain-table"/> 
    <xsl:apply-templates select="amqp" mode="domain-consts"/> 
  </xsl:template> 

  <!--
  ======================
  Template: domain-table
  ======================
  Generates the domain name to C++ type lookup table
  which is required for later generation.
  Format:
  <domains>
    <domain doamin-name="dname1" cpp-type="type1"/>
    <domain doamin-name="dname2" cpp-type="type2"/>
    ...
  </domains>
  -->
  <xsl:template match="amqp" mode="domain-table">
    <domains><xsl:text>&#xA;</xsl:text>
      <xsl:for-each select="domain">
        <xsl:text>  </xsl:text><domain>
          <xsl:attribute name="domain-name">
            <xsl:value-of select="@name"/>
          </xsl:attribute>
          <xsl:attribute name="cpp-type">
            <xsl:value-of select="amqp:cpp-type(@type)"/>
          </xsl:attribute>
        </domain><xsl:text>&#xA;</xsl:text>
      </xsl:for-each>
    </domains>
  </xsl:template>

  <!--
  =======================
  Template: domain-consts
  =======================
  Generates a header file (AMQP_Constants.h) containing definitions of
  all the <constant> declarations in the AMQP XML specification.
  -->
  <xsl:template match="amqp" mode="domain-consts">
    <xsl:result-document href="AMQP_Constants.h" format="textFormat">
      <xsl:value-of select="amqp:copyright()"/>
      <xsl:text>
#ifndef _AMQP_Constants_
#define _AMQP_Constants_

#include "qpid/framing/amqp_types.h"

namespace qpid {
namespace framing {

/**** Constants ****/&#xA;&#xA;</xsl:text>
      <xsl:for-each select="constant">
        <xsl:if test="doc">
          <xsl:text>&#xA;/*&#xA;</xsl:text>
          <xsl:value-of select="normalize-space(doc)"/>
          <xsl:text>&#xA;*/&#xA;</xsl:text>
        </xsl:if>
        <xsl:text>const u_int16_t </xsl:text><xsl:value-of select="concat('AMQP_', upper-case(amqp:cpp-name(@name)), ' = ', @value)"/><xsl:text>;&#xA;</xsl:text>
      </xsl:for-each>
      <xsl:text>

} /* namespace framing */
} /* namespace qpid */

#endif&#xA;</xsl:text>
    </xsl:result-document>
  </xsl:template>

</xsl:stylesheet> 
