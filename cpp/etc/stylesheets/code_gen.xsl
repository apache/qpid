<?xml version='1.0'?> 
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amqp="http://amqp.org"> 

  <xsl:import href="convert_0.81.xsl"/>
  <xsl:import href="amqp_consts.xsl"/>
  <xsl:import href="amqp_server_operations.xsl"/>
  <xsl:import href="amqp_client_operations.xsl"/>
  <xsl:import href="amqp_server.xsl"/>
  <xsl:import href="amqp_client.xsl"/>
  <xsl:import href="amqp_server_handler_impl.xsl"/>
  <xsl:import href="amqp_client_handler_impl.xsl"/>

  <xsl:output method="text" indent="yes" name="textFormat"/> 
  <xsl:key name="domain-lookup" match="domains/domain" use="@domain-name"/>

  <xsl:template match="/">

    <!-- 0. Convert to 0.81 format -->
    <!--
    NOTE: The XML specification change from 0.8 to 0.81 is primarily a change to
    the XML itself, not the protocol it represents. However, at the time of this
    commit, the 0.81 specification has not been approved by the AMQP working group,
    so this converter from the 0.8 format to the 0.81 format has been included as
    a temporary measure. When the 0.81 format becomes official, then this conversion
    should be removed, and all of the templates below will revert to select=".".

    TODO: Remove this conversion when the new 0.81 spec is checked in.
    -->
    <xsl:variable name="format-v081">
      <xsl:apply-templates mode="do-amqp" select="amqp"/>
    </xsl:variable>
    <!-- == Uncomment this to view output for debugging ==
    <xsl:result-document href="convert_081.out">
        <xsl:copy-of select="$format-v081"/> 
    </xsl:result-document>
    -->

    <!-- 1. Domain to C++ type lookup table -->
    <xsl:variable name="domain-cpp-table">
      <xsl:apply-templates mode="domain-table" select="$format-v081"/>
    </xsl:variable>
    <!-- == Uncomment this to view output for debugging ==
    <xsl:result-document href="domain_cpp_table.out">
        <xsl:copy-of select="$domain-cpp-table"/> 
    </xsl:result-document>
    -->

    <!-- 2. Constant declarations (AMQP_Constants.h) -->
    <xsl:apply-templates mode="domain-consts" select="$format-v081"/>

    <!-- 3. Client and server handler pure virtual classes -->
    <xsl:apply-templates mode="server-operations-h" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>
    <xsl:apply-templates mode="client-operations-h" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>

    <!-- 4. Client and server output classes -->
    <xsl:apply-templates mode="server_h" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>
    <xsl:apply-templates mode="client_h" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>
    <xsl:apply-templates mode="server_cpp" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>
    <xsl:apply-templates mode="client_cpp" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>

    <!-- 5. Client and server handler stub classes -->
    <xsl:apply-templates mode="server_handler_impl_h" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>
    <xsl:apply-templates mode="client_handler_impl_h" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>
    <!-- TODO: Find a way to only run the .cpp stub generator when required, as
    running this will overwrite any stub code in existance! -->
    <xsl:apply-templates mode="server_handler_impl_cpp" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>
    <xsl:apply-templates mode="client_handler_impl_cpp" select="$format-v081">
      <xsl:with-param name="domain-cpp-table" select="$domain-cpp-table"/>
    </xsl:apply-templates>

 </xsl:template>

</xsl:stylesheet> 
