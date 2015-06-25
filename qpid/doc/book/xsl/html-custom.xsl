<?xml version='1.0'?> 
<!--
 -
 - Licensed to the Apache Software Foundation (ASF) under one
 - or more contributor license agreements.  See the NOTICE file
 - distributed with this work for additional information
 - regarding copyright ownership.  The ASF licenses this file
 - to you under the Apache License, Version 2.0 (the
 - "License"); you may not use this file except in compliance
 - with the License.  You may obtain a copy of the License at
 - 
 -   http://www.apache.org/licenses/LICENSE-2.0
 - 
 - Unless required by applicable law or agreed to in writing,
 - software distributed under the License is distributed on an
 - "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 - KIND, either express or implied.  See the License for the
 - specific language governing permissions and limitations
 - under the License.
 -
 -->
<xsl:stylesheet  
       xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  version="1.0"> 
  <xsl:import href="http://docbook.sourceforge.net/release/xsl/current/html/chunk.xsl"/>

<xsl:template name="chunk-element-content">
  <xsl:param name="prev"/>
  <xsl:param name="next"/>
  <xsl:param name="nav.context"/>
  <xsl:param name="content">
    <xsl:apply-imports/>
  </xsl:param>

  <xsl:call-template name="user.preroot"/>

  <html>
    <xsl:call-template name="html.head">
      <xsl:with-param name="prev" select="$prev"/>
      <xsl:with-param name="next" select="$next"/>
    </xsl:call-template>

    <body>
<div class="container">
      <xsl:call-template name="body.attributes"/>
      <xsl:call-template name="user.header.navigation"/>

      <xsl:call-template name="header.navigation">
        <xsl:with-param name="prev" select="$prev"/>
        <xsl:with-param name="next" select="$next"/>
        <xsl:with-param name="nav.context" select="$nav.context"/>
      </xsl:call-template>

<div class="main_text_area">
 <div class="main_text_area_top">
 </div>
 <div class="main_text_area_body">
      <xsl:call-template name="user.header.content"/>
      <xsl:copy-of select="$content"/>

      <xsl:call-template name="user.footer.content"/>
 </div>
      <xsl:call-template name="footer.navigation">
        <xsl:with-param name="prev" select="$prev"/>
        <xsl:with-param name="next" select="$next"/>
        <xsl:with-param name="nav.context" select="$nav.context"/>
      </xsl:call-template>

      <xsl:call-template name="user.footer.navigation"/>
 <div class="main_text_area_bottom">
 </div>
</div>
</div>
    </body>
  </html>
  <xsl:value-of select="$chunk.append"/>
</xsl:template>

<xsl:template name="breadcrumbs">
  <xsl:param name="this.node" select="."/>
  <DIV class="breadcrumbs">
    <xsl:for-each select="$this.node/ancestor::*">
      <span class="breadcrumb-link">
        <a>
          <xsl:attribute name="href">
            <xsl:call-template name="href.target">
              <xsl:with-param name="object" select="."/>
              <xsl:with-param name="context" select="$this.node"/>
            </xsl:call-template>
          </xsl:attribute>
          <xsl:apply-templates select="." mode="title.markup"/>
        </a>
      </span>
      <xsl:text> &gt; </xsl:text>
    </xsl:for-each>
    <!-- And display the current node, but not as a link -->
    <span class="breadcrumb-node">
      <xsl:apply-templates select="$this.node" mode="title.markup"/>
    </span>
  </DIV>
</xsl:template>

<xsl:template name="header.navigation">
   <DIV class="header">
      <DIV class="logo">
        <H1>Apache Qpid&#8482;</H1>
        <H2>Open Source AMQP Messaging</H2>
      </DIV>
   </DIV>

   <DIV class="menu_box">
     <DIV class="menu_box_top"></DIV>
     <DIV class="menu_box_body">
       <H3>Apache Qpid</H3>
       <UL>
         <LI><A href="http://qpid.apache.org/index.html">Home</A></LI>
         <LI><A href="http://qpid.apache.org/download.html">Download</A></LI>
         <LI><A href="http://qpid.apache.org/getting_started.html">Getting Started</A></LI>
         <LI><A href="http://www.apache.org/licenses/">License</A></LI>
         <LI><A href="https://cwiki.apache.org/qpid/faq.html">FAQ</A></LI>
       </UL>
     </DIV>
     <DIV class="menu_box_bottom"></DIV>

     <DIV class="menu_box_top"></DIV>
     <DIV class="menu_box_body">
       <H3>Documentation</H3>
       <UL>
         <LI><A href="http://qpid.apache.org/documentation.html#doc-release">Latest Release</A></LI>
         <LI><A href="http://qpid.apache.org/documentation.html#doc-trunk">Trunk</A></LI>
         <LI><A href="http://qpid.apache.org/documentation.html#doc-archives">Archive</A></LI>
       </UL>
     </DIV>
     <DIV class="menu_box_bottom"></DIV>

     <DIV class="menu_box_top"></DIV>
     <DIV class="menu_box_body">
       <H3>Community</H3> 
       <UL>
         <LI><A href="http://qpid.apache.org/getting_involved.html">Getting Involved</A></LI>
         <LI><A href="http://qpid.apache.org/source_repository.html">Source Repository</A></LI>
         <LI><A href="http://qpid.apache.org/mailing_lists.html">Mailing Lists</A></LI>
         <LI><A href="https://cwiki.apache.org/qpid/">Wiki</A></LI>
         <LI><A href="https://issues.apache.org/jira/browse/qpid">Issue Reporting</A></LI>
         <LI><A href="http://qpid.apache.org/people.html">People</A></LI>
         <LI><A href="http://qpid.apache.org/acknowledgements.html">Acknowledgements</A></LI>
       </UL>
     </DIV>
     <DIV class="menu_box_bottom"></DIV>

     <DIV class="menu_box_top"></DIV>
     <DIV class="menu_box_body">
       <H3>Developers</H3>
       <UL>
         <LI><A href="https://cwiki.apache.org/qpid/building.html">Building Qpid</A></LI>
         <LI><A href="https://cwiki.apache.org/qpid/developer-pages.html">Developer Pages</A></LI>
       </UL>
     </DIV>
     <DIV class="menu_box_bottom"></DIV>

     <DIV class="menu_box_top"></DIV>
     <DIV class="menu_box_body">
       <H3>About AMQP</H3>
       <UL>
         <LI><A href="http://qpid.apache.org/amqp.html">What is AMQP?</A></LI>
       </UL>
     </DIV>
     <DIV class="menu_box_bottom"></DIV>

     <DIV class="menu_box_top"></DIV>
     <DIV class="menu_box_body">
       <H3>About Apache</H3>
       <UL>
         <LI><A href="http://www.apache.org">Home</A></LI>
         <LI><A href="http://www.apache.org/foundation/sponsorship.html">Sponsorship</A></LI>
         <LI><A href="http://www.apache.org/foundation/thanks.html">Thanks</A></LI>
         <LI><A href="http://www.apache.org/security/">Security</A></LI>
       </UL>
     </DIV>
     <DIV class="menu_box_bottom"></DIV>
   </DIV>

</xsl:template> 

<xsl:template name="user.header.content">
  <xsl:call-template name="breadcrumbs"/>
</xsl:template>

</xsl:stylesheet>
