<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org"> 

  <xsl:import href="code_utils.xsl"/>

  <!--
  ==================
  Template: server_h
  ==================
  Server header file.
  -->
  <xsl:template match="amqp" mode="server_h">
    <xsl:param name="domain-cpp-table"/>
    <xsl:result-document href="AMQP_ClientProxy.h" format="textFormat">
      <xsl:value-of select="amqp:copyright()"/>
#ifndef _AMQP_ClientProxy_
#define _AMQP_ClientProxy_

#include "AMQP_ClientOperations.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/OutputHandler.h"

namespace qpid {
namespace framing {

class AMQP_ClientProxy : virtual public AMQP_ClientOperations
{
    public:

        AMQP_ClientProxy(OutputHandler* _out);
        virtual ~AMQP_ClientProxy() {};

    <!-- inner classes -->
    <xsl:for-each select="class">
        <xsl:variable name="class" select="amqp:cpp-class-name(@name)"/>
        <xsl:if test="doc">
          <xsl:text>&#xA;/**&#xA;===== Class: </xsl:text><xsl:value-of select="$class"/><xsl:text> =====&#xA;</xsl:text>
          <xsl:value-of select="amqp:process-docs(doc)"/>
          <xsl:text>&#xA;*/&#xA;</xsl:text>
        </xsl:if>
        <xsl:text>        class </xsl:text><xsl:value-of select="$class"/><xsl:text> :  virtual public AMQP_ClientOperations::</xsl:text><xsl:value-of select="$class"/><xsl:text>Handler
        {
                OutputHandler* out;

            public:
                /* Constructors and destructors */
                </xsl:text><xsl:value-of select="$class"/><xsl:text>(OutputHandler* _out);
                virtual ~</xsl:text><xsl:value-of select="$class"/><xsl:text>();
                
                /* Protocol methods */&#xA;</xsl:text>
        <xsl:for-each select="method">
          <xsl:if test="chassis[@name='client']">
            <xsl:variable name="method" select="amqp:cpp-name(@name)"/>
            <xsl:if test="doc">
              <xsl:text>&#xA;/**&#xA;----- Method: </xsl:text><xsl:value-of select="$class"/><xsl:text>.</xsl:text><xsl:value-of select="$method"/><xsl:text> -----&#xA;</xsl:text>
              <xsl:value-of select="amqp:process-docs(doc)"/>
              <xsl:text>&#xA;*/&#xA;</xsl:text>
            </xsl:if>
            <xsl:for-each select="rule">
              <xsl:text>&#xA;/**&#xA;</xsl:text>
              <xsl:text>Rule "</xsl:text><xsl:value-of select="@name"/><xsl:text>":&#xA;</xsl:text><xsl:value-of select="amqp:process-docs(doc)"/>
              <xsl:text>&#xA;*/&#xA;</xsl:text>
            </xsl:for-each>
            <xsl:text>                virtual void </xsl:text><xsl:value-of select="$method"/>
            <xsl:text>( u_int16_t channel</xsl:text><xsl:if test="field"><xsl:text>,&#xA;                        </xsl:text>
            <xsl:for-each select="field">
              <xsl:variable name="domain-cpp-type" select="amqp:cpp-lookup(@domain, $domain-cpp-table)"/>
              <xsl:value-of select="concat($domain-cpp-type, amqp:cpp-arg-ref($domain-cpp-type), ' ', amqp:cpp-name(@name))"/>
              <xsl:if test="position()!=last()">
                <xsl:text>,&#xA;                        </xsl:text>
              </xsl:if>
            </xsl:for-each>
          </xsl:if>
            <xsl:text> );&#xA;</xsl:text>
          </xsl:if>
        </xsl:for-each>
        <xsl:text>        }; /* class </xsl:text><xsl:value-of select="$class"/> */
      </xsl:for-each>

    <!-- Accessors for each nested class instance -->
    <xsl:for-each select="class">
        <xsl:value-of select="concat(amqp:cpp-class-name(@name), '&amp; get', amqp:cpp-class-name(@name), '()')"/>;
    </xsl:for-each>

    private:

    OutputHandler* out;

    <!-- An instance of each nested class -->
    <xsl:for-each select="class">
        <xsl:value-of select="concat(amqp:cpp-class-name(@name), ' ', amqp:cpp-name(@name))"/>;
    </xsl:for-each>



      }; /* class AMQP_ClientProxy */

} /* namespace framing */
} /* namespace qpid */

#endif
</xsl:result-document>
  </xsl:template>


  <!--
  ====================
  Template: server_cpp
  ====================
  Server body.
  -->
  <xsl:template match="amqp" mode="server_cpp">
    <xsl:param name="domain-cpp-table"/>
    <xsl:result-document href="AMQP_ClientProxy.cpp" format="textFormat">
      <xsl:value-of select="amqp:copyright()"/>

#include "AMQP_ClientProxy.h"

namespace qpid {
namespace framing {

AMQP_ClientProxy::AMQP_ClientProxy(OutputHandler* _out) :
    out(_out),
    <!-- Initialisation of each nested class instance -->
    <xsl:for-each select="class">
        <xsl:value-of select="concat(amqp:cpp-name(@name), '(_out)')"/>
        <xsl:if test="position()!=last()">,
        </xsl:if>
    </xsl:for-each>

{
}

      <xsl:for-each select="class">
        <xsl:variable name="class" select="amqp:cpp-class-name(@name)"/>
        <xsl:text>&#xA;/* ++++++++++ Class: </xsl:text><xsl:value-of select="$class"/><xsl:text> ++++++++++ */

AMQP_ClientProxy::</xsl:text><xsl:value-of select="$class"/><xsl:text>::</xsl:text><xsl:value-of select="$class"/><xsl:text>(OutputHandler* _out) :
    out(_out)
{
}

AMQP_ClientProxy::</xsl:text><xsl:value-of select="$class"/><xsl:text>::~</xsl:text><xsl:value-of select="$class"/><xsl:text>() {}&#xA;&#xA;</xsl:text>
        <xsl:for-each select="method">
          <xsl:if test="chassis[@name='client']">
            <xsl:text>void AMQP_ClientProxy::</xsl:text><xsl:value-of select="$class"/><xsl:text>::</xsl:text>
            <xsl:value-of select="amqp:cpp-name(@name)"/><xsl:text>( u_int16_t channel</xsl:text><xsl:if test="field">
            <xsl:text>,&#xA;                        </xsl:text>
            <xsl:for-each select="field">
              <xsl:variable name="domain-cpp-type" select="amqp:cpp-lookup(@domain, $domain-cpp-table)"/>
              <xsl:value-of select="concat($domain-cpp-type, amqp:cpp-arg-ref($domain-cpp-type), ' ', amqp:cpp-name(@name))"/>
              <xsl:if test="position()!=last()">
                <xsl:text>,&#xA;                        </xsl:text>
              </xsl:if>
            </xsl:for-each>
          </xsl:if>
          <xsl:text> )
{
    out->send( new AMQFrame( channel,
        new </xsl:text><xsl:value-of select="concat($class, amqp:field-name(@name), 'Body')"/><xsl:text>( </xsl:text>
          <xsl:for-each select="field">
            <xsl:value-of select="amqp:cpp-name(@name)"/>
            <xsl:if test="position()!=last()">
              <xsl:text>,&#xA;            </xsl:text>
            </xsl:if>
            </xsl:for-each>
            <xsl:text> ) ) );
}&#xA;&#xA;</xsl:text>
          </xsl:if>
        </xsl:for-each>
      </xsl:for-each>

    <!-- Accessors for each nested class instance -->
    <xsl:for-each select="class">
        <xsl:value-of select="concat('AMQP_ClientProxy::', amqp:cpp-class-name(@name), '&amp; AMQP_ClientProxy::get', amqp:cpp-class-name(@name), '()')"/>{
        <xsl:value-of select="concat('    return ', amqp:cpp-name(@name))"/>;
        }

    </xsl:for-each>

      <xsl:text>
} /* namespace framing */
} /* namespace qpid */&#xA;</xsl:text>
    </xsl:result-document>
  </xsl:template>

</xsl:stylesheet> 
