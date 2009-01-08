<?xml version='1.0'?> 
<!--
 
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at
 
   http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 
-->

<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:amq="http://amq.org"> 

<!-- this class contains the templates for generating C# source code for a given framing model -->
<xsl:import href="utils.xsl"/>
<xsl:output method="text" indent="yes" name="textFormat"/> 

<xsl:param name="registry_name"/>

<xsl:template match="/"> 
    <xsl:apply-templates mode="generate-multi" select="frames"/>
    <xsl:apply-templates mode="generate-registry" select="frames"/>
</xsl:template>

<!-- processes all frames outputting the classes in a single stream -->
<!-- (useful for debugging etc) -->
<xsl:template match="frame" mode="generate-single"> 
    <xsl:call-template name="generate-class">
        <xsl:with-param name="f" select="."/>
    </xsl:call-template>
</xsl:template>

<!-- generates seperate file for each class/frame -->
<xsl:template match="frame" mode="generate-multi"> 
    <xsl:variable name="uri" select="concat(@name, '.cs')"/> 
    wrote <xsl:value-of select="$uri"/> 
    <xsl:result-document href="{$uri}" format="textFormat"> 
    <xsl:call-template name="generate-class">
        <xsl:with-param name="f" select="."/>
    </xsl:call-template>
    </xsl:result-document> 
</xsl:template> 

<!-- main class generation template -->
<xsl:template name="generate-class"> 
    <xsl:param name="f"/>
using Apache.Qpid.Buffer;
using System.Text;

namespace Apache.Qpid.Framing
{
  ///
  /// <summary>This class is autogenerated
  /// Do not modify.
  ///</summary>
  /// @author Code Generator Script by robert.j.greig@jpmorgan.com
  public class <xsl:value-of select="$f/@name"/> : AMQMethodBody , IEncodableAMQDataBlock
  {
    public const int CLASS_ID = <xsl:value-of select="$f/@class-id"/>; 	
    public const int METHOD_ID = <xsl:value-of select="$f/@method-id"/>; 	

    <xsl:for-each select="$f/field"> 
        <xsl:text>public </xsl:text><xsl:value-of select="@csharp-type"/>
        <xsl:text> </xsl:text>
        <xsl:value-of select="@name"/>;    
    </xsl:for-each> 

    protected override ushort Clazz
    {
        get
        {
            return <xsl:value-of select="$f/@class-id"/>;
        }
    }
   
    protected override ushort Method
    {
        get
        {
            return <xsl:value-of select="$f/@method-id"/>;
        }
    }

    protected override uint BodySize
    {
    get
    {
        <xsl:choose> 
        <xsl:when test="$f/field">
        return (uint)
        <xsl:for-each select="$f/field">
            <xsl:if test="position() != 1">+
            </xsl:if>
            <xsl:value-of select="amq:field-length(.)"/>
        </xsl:for-each>		 
        ;
        </xsl:when>
        <xsl:otherwise>return 0;</xsl:otherwise>
        </xsl:choose> 
    }
    }

    protected override void WriteMethodPayload(ByteBuffer buffer)
    {
        <xsl:for-each select="$f/field">
            <xsl:if test="@type != 'bit'">
                <xsl:value-of select="amq:encoder(.)"/>;
            </xsl:if>
            <xsl:if test="@type = 'bit' and @boolean-index = 1">
                <xsl:text>EncodingUtils.WriteBooleans(buffer, new bool[]{</xsl:text>
                <xsl:value-of select="$f/field[@type='bit']/@name" separator=", "/>});
            </xsl:if>
        </xsl:for-each>		 
    }

    protected override void PopulateMethodBodyFromBuffer(ByteBuffer buffer)
    {
        <xsl:for-each select="$f/field">
            <xsl:value-of select="amq:decoder(.)"/>;
        </xsl:for-each>		 
    }

    public override string ToString()
    {
        StringBuilder buf = new StringBuilder(base.ToString());
        <xsl:for-each select="$f/field">
            <xsl:text>buf.Append(" </xsl:text><xsl:value-of select="@name"/>: ").Append(<xsl:value-of select="@name"/>);
        </xsl:for-each> 
        return buf.ToString();
    }

    public static AMQFrame CreateAMQFrame(ushort channelId<xsl:if test="$f/field">, </xsl:if><xsl:value-of select="$f/field/concat(@csharp-type, ' ', @name)" separator=", "/>)
    {
        <xsl:value-of select="@name"/> body = new <xsl:value-of select="@name"/>();
        <xsl:for-each select="$f/field">
            <xsl:value-of select="concat('body.', @name, ' = ', @name)"/>;
        </xsl:for-each>		 
        AMQFrame frame = new AMQFrame();
        frame.Channel = channelId;
        frame.BodyFrame = body;
        return frame;
    }
} 
}
</xsl:template> 

<xsl:template match="/" mode="generate-registry">
     <xsl:text>Matching root for registry mode!</xsl:text>
     <xsl:value-of select="."/> 
     <xsl:apply-templates select="frames" mode="generate-registry"/>
</xsl:template>

<xsl:template match="registries" mode="generate-registry">
Wrote MethodBodyDecoderRegistry.cs
    <xsl:result-document href="MethodBodyDecoderRegistry.cs" format="textFormat">
using System;
using System.Collections;
using log4net;

namespace Apache.Qpid.Framing
{


  ///
  /// <summary>This class is autogenerated
  /// Do not modify.
  /// </summary>
  /// @author Code Generator Script by robert.j.greig@jpmorgan.com

  public class MethodBodyDecoderRegistry
  {
    private static readonly ILog _log = LogManager.GetLogger(typeof(MethodBodyDecoderRegistry));

    private static readonly Hashtable _classMethodProductToMethodBodyMap = new Hashtable();

    static MethodBodyDecoderRegistry()
    {
      <xsl:for-each select="registry">
            <xsl:value-of select="concat(@name, '.Register(_classMethodProductToMethodBodyMap)')"/>;         
        </xsl:for-each>
      }

      public static AMQMethodBody Get(int clazz, int method) 
      {
      Type bodyClass = (Type) _classMethodProductToMethodBodyMap[clazz * 1000 + method];
      if (bodyClass != null)
      {
        try
        {
          return (AMQMethodBody) Activator.CreateInstance(bodyClass);
        }
        catch (Exception e)
        {
          throw new AMQFrameDecodingException(_log, "Unable to instantiate body class for class " + clazz + " and method " + method + ": " + e, e);
        }
      }
      else
      {
        throw new AMQFrameDecodingException(_log, "Unable to find a suitable decoder for class " + clazz + " and method " + method);
      }
    }
  }
  }
    </xsl:result-document>
</xsl:template>

<xsl:template match="frames" mode="list-registry">	
    <xsl:if test="$registry_name">

    <xsl:variable name="file" select="concat($registry_name, '.cs')"/> 
    wrote <xsl:value-of select="$file"/> 
    <xsl:result-document href="{$file}" format="textFormat">

using System.Collections;
namespace Apache.Qpid.Framing
{
  /**
   * This class is autogenerated, do not modify. [From <xsl:value-of select="@protocol"/>]
   */
  class <xsl:value-of select="$registry_name"/>
  {
    internal static void Register(Hashtable map)
    {
        <xsl:for-each select="frame">
            <xsl:text>map[</xsl:text>
            <xsl:value-of select="@class-id"/>         
	    <xsl:text> * 1000 + </xsl:text> 
            <xsl:value-of select="@method-id"/>         
	    <xsl:text>] = typeof(</xsl:text> 
            <xsl:value-of select="@name"/>);
        </xsl:for-each>
    }
}
}
    </xsl:result-document>

    </xsl:if>
</xsl:template>

</xsl:stylesheet>
