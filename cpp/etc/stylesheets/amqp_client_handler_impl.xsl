<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org"> 

  <xsl:import href="code_utils.xsl"/>

  <!--
  ===============================
  Template: client_handler_impl_h
  ===============================
  Template to generate the AMQP_ClientHandlerImpl class header file.
  -->
  <xsl:template match="amqp" mode="client_handler_impl_h">
    <xsl:param name="domain-cpp-table"/>
    <xsl:result-document href="AMQP_ClientHandlerImpl.h" format="textFormat">
      <xsl:value-of select="amqp:copyright()"/>
      <xsl:text>
#ifndef _AMQP_ClientHandlerImpl_
#define _AMQP_ClientHandlerImpl_

#include "AMQP_ClientOperations.h"
#include "qpid/framing/FieldTable.h"

namespace qpid {
namespace framing {

class AMQP_ClientHandlerImpl : virtual public AMQP_ClientOperations
{&#xA;</xsl:text>

      <!-- List of pointers to each inner class instance -->
      <xsl:for-each select="class">
        <xsl:variable name="class" select="concat(amqp:cpp-class-name(@name), 'Handler')"/>
        <xsl:text>        AMQP_ClientOperations::</xsl:text><xsl:value-of select="$class"/><xsl:text>* </xsl:text>
        <xsl:value-of select="$class"/><xsl:text>Ptr;&#xA;</xsl:text>
      </xsl:for-each>
      <xsl:text>
    public:
        AMQP_ClientHandlerImpl();
        virtual ~AMQP_ClientHandlerImpl();&#xA;&#xA;</xsl:text>        

      <!-- List of functions to return pointer to each inner class instance -->
      <xsl:for-each select="class">
        <xsl:variable name="class" select="concat(amqp:cpp-class-name(@name), 'Handler')"/>
        <xsl:text>        inline AMQP_ClientOperations::</xsl:text>
        <xsl:value-of select="$class"/><xsl:text>* get</xsl:text><xsl:value-of select="$class"/>
        <xsl:text>() { return </xsl:text><xsl:value-of select="$class"/><xsl:text>Ptr; }&#xA;</xsl:text>
      </xsl:for-each>
      <xsl:text>&#xA;</xsl:text>

      <!-- Inner classes -->
      <xsl:for-each select="class">
        <xsl:variable name="class" select="concat(amqp:cpp-class-name(@name), 'Handler')"/>

        <!-- Inner class documentation & rules -->
        <xsl:if test="doc">
          <xsl:text>&#xA;/**&#xA;</xsl:text>
          <xsl:text>===== Class: </xsl:text><xsl:value-of select="$class"/><xsl:text>Impl =====&#xA;</xsl:text>
          <xsl:value-of select="amqp:process-docs(doc)"/>
          <xsl:text>*/&#xA;</xsl:text>
        </xsl:if>

        <!-- Inner class definition -->
        <xsl:text>        class </xsl:text><xsl:value-of select="$class"/>
        <xsl:text>Impl : virtual public AMQP_ClientOperations::</xsl:text><xsl:value-of select="$class"/>
        <xsl:text>&#xA;        {
            public:
                /* Constructors and destructors */
                </xsl:text><xsl:value-of select="$class"/><xsl:text>Impl();
                virtual ~</xsl:text><xsl:value-of select="$class"/><xsl:text>Impl();
                
                /* Protocol methods */&#xA;</xsl:text>

        <!-- Inner class methods (only if the chassis is set to "client") -->
        <xsl:for-each select="method">
          <xsl:if test="chassis[@name='client']">
            <xsl:variable name="method" select="amqp:cpp-name(@name)"/>

            <!-- Inner class method documentation & rules -->
            <xsl:if test="doc">
              <xsl:text>&#xA;/**&#xA;</xsl:text>
              <xsl:text>----- Method: </xsl:text><xsl:value-of select="$class"/>
              <xsl:text>Impl.</xsl:text><xsl:value-of select="@name"/><xsl:text> -----&#xA;</xsl:text>
              <xsl:value-of select="amqp:process-docs(doc)"/>
              <xsl:text>*/&#xA;</xsl:text>
            </xsl:if>
            <xsl:for-each select="rule">
              <xsl:text>&#xA;/**&#xA;</xsl:text>
              <xsl:text>Rule "</xsl:text><xsl:value-of select="@name"/><xsl:text>":&#xA;</xsl:text>
              <xsl:value-of select="amqp:process-docs(doc)"/>
              <xsl:text>*/&#xA;</xsl:text>
            </xsl:for-each>

            <!-- Inner class method definition -->
            <xsl:text>&#xA;                virtual void </xsl:text><xsl:value-of select="$method"/>
            <xsl:text>( u_int16_t channel</xsl:text>

            <!-- Inner class method parameter definition -->
            <xsl:if test="field">
              <xsl:text>,&#xA;                        </xsl:text>
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
        <xsl:text>&#xA;        }; /* class </xsl:text><xsl:value-of select="$class"/><xsl:text>Impl */&#xA;</xsl:text>
      </xsl:for-each>
      <xsl:text>&#xA;}; /* AMQP_ClientHandlerImpl */

} /* namespace framing */
} /* namespace qpid */

#endif&#xA;</xsl:text>
    </xsl:result-document>
  </xsl:template>

  <!--
  =================================
  Template: client_handler_impl_cpp
  =================================
  Template to generate the AMQP_ClientHandlerImpl class stubs.
  -->
  <xsl:template match="amqp" mode="client_handler_impl_cpp">
    <xsl:param name="domain-cpp-table"/>
    <xsl:result-document href="AMQP_ClientHandlerImpl.cpp" format="textFormat">
      <xsl:value-of select="amqp:copyright()"/>
      <xsl:text>
#include "AMQP_ClientHandlerImpl.h"

namespace qpid {
namespace framing {

AMQP_ClientHandlerImpl::AMQP_ClientHandlerImpl() :&#xA;        </xsl:text>
      <xsl:for-each select="class">
        <xsl:variable name="class" select="amqp:cpp-class-name(@name)"/>
        <xsl:value-of select="$class"/>
        <xsl:text>HandlerPtr( new </xsl:text><xsl:value-of select="$class"/><xsl:text>HandlerImpl() )</xsl:text>
        <xsl:if test="position()!=last()">
          <xsl:text>,&#xA;        </xsl:text>
        </xsl:if>
      </xsl:for-each>
      <xsl:text>
{
}

AMQP_ClientHandlerImpl::~AMQP_ClientHandlerImpl()
{&#xA;</xsl:text>
      <xsl:for-each select="class">
        <xsl:text>        delete </xsl:text><xsl:value-of select="amqp:cpp-class-name(@name)"/><xsl:text>HandlerPtr;&#xA;</xsl:text>
      </xsl:for-each>}

      <xsl:for-each select="class">
        <xsl:variable name="class" select="amqp:cpp-class-name(@name)"/>
        <xsl:text>&#xA;/* ===== Class: </xsl:text><xsl:value-of select="$class"/><xsl:text>HandlerImpl ===== */&#xA;&#xA;</xsl:text>
        <xsl:text>AMQP_ClientHandlerImpl::</xsl:text><xsl:value-of select="$class"/><xsl:text>HandlerImpl::</xsl:text>
        <xsl:value-of select="$class"/><xsl:text>HandlerImpl()&#xA;{&#xA;}&#xA;&#xA;</xsl:text>
        <xsl:text>AMQP_ClientHandlerImpl::</xsl:text><xsl:value-of select="$class"/><xsl:text>HandlerImpl::~</xsl:text>
        <xsl:value-of select="$class"/><xsl:text>HandlerImpl()&#xA;{&#xA;}&#xA;&#xA;</xsl:text>
        <xsl:for-each select="method">
          <xsl:if test="chassis[@name='client']">
            <xsl:text>void AMQP_ClientHandlerImpl::</xsl:text><xsl:value-of select="$class"/><xsl:text>HandlerImpl::</xsl:text>
            <xsl:value-of select="amqp:cpp-name(@name)"/><xsl:text>( u_int16_t /*channel*/</xsl:text>
            <xsl:if test="field">
              <xsl:text>,&#xA;                        </xsl:text>
              <xsl:for-each select="field">
                <xsl:variable name="domain-cpp-type" select="amqp:cpp-lookup(@domain, $domain-cpp-table)"/>
                <xsl:value-of select="concat($domain-cpp-type, amqp:cpp-arg-ref($domain-cpp-type), ' /*', amqp:cpp-name(@name), '*/')"/>
                <xsl:if test="position()!=last()">
                  <xsl:text>,&#xA;                        </xsl:text>
                </xsl:if>
              </xsl:for-each>
            </xsl:if><xsl:text> )&#xA;{&#xA;}&#xA;&#xA;</xsl:text>
          </xsl:if>
        </xsl:for-each>
      </xsl:for-each>
      <xsl:text>

} /* namespace framing */
} /* namespace qpid */&#xA;&#xA;</xsl:text>
    </xsl:result-document>
  </xsl:template>

</xsl:stylesheet> 
