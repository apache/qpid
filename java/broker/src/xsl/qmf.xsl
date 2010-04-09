<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output omit-xml-declaration="yes" />
<xsl:template match="/">
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.qpid.qmf.schema;

import org.apache.qpid.qmf.*;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.transport.codec.BBDecoder;

import java.util.Arrays;
import java.util.UUID;
import java.util.Map;

    <xsl:apply-templates select="descendant-or-self::node()[name()='schema']" mode="schema"/>
    
</xsl:template>
    
<xsl:template match="node()[name()='schema']" mode="schema">
    
<xsl:variable name="schemaName"><xsl:call-template name="substringAfterLast"><xsl:with-param name="input" select="@package"/><xsl:with-param name="arg">.</xsl:with-param></xsl:call-template></xsl:variable>
<xsl:variable name="ClassName"><xsl:call-template name="initCap"><xsl:with-param name="input" select="$schemaName"></xsl:with-param></xsl:call-template>Schema</xsl:variable>
<xsl:variable name="classList"><xsl:apply-templates select="node()[name()='class']" mode="classList"/></xsl:variable>
<xsl:variable name="eventList"><xsl:apply-templates select="node()[name()='event']" mode="eventList"/></xsl:variable>        

public class <xsl:value-of select="$ClassName"/> extends QMFPackage
{
    private static final byte QMF_VERSION = (byte) '2';
    
    private static final BrokerSchema PACKAGE = new <xsl:value-of select="$ClassName"/>();
    private static final String SCHEMA_NAME = "<xsl:value-of select="@package"/>";
    
<xsl:text disable-output-escaping="yes">
    
    protected abstract class QMFInfoCommand&lt;T extends QMFObject&gt; extends QMFCommand
    {
        private final T _object;
        private final long _sampleTime;
        
        
        protected QMFInfoCommand(QMFCommand trigger, QMFOperation op, T object, long sampleTime)
        {
            this(trigger.getHeader().getVersion(),
                 trigger.getHeader().getSeq(),
                 op,
                 object,
                 sampleTime);
        }
        
        protected QMFInfoCommand(QMFOperation op, T object, long sampleTime)
        {
            this(QMF_VERSION,0,op,object,sampleTime);
        }
        
        private QMFInfoCommand(final byte qmfVersion,
                               final int seq,
                               final QMFOperation op,
                               final T object,
                               final long sampleTime)
        {
            super(new QMFCommandHeader(qmfVersion, seq,op));
            _object = object;
            _sampleTime = sampleTime;
        }
          
        public T getObject()
        {
            return _object;
        }
        
        @Override
        public void encode(final BBEncoder encoder)
        {
            super.encode(encoder);
            encoder.writeStr8(SCHEMA_NAME);
            encoder.writeStr8(_object.getQMFClass().getName());
            encoder.writeBin128(new byte[16]);
            encoder.writeUint64(_sampleTime * 1000000L);
            encoder.writeUint64(_object.getCreateTime() * 1000000L);
            encoder.writeUint64(_object.getDeleteTime() * 1000000L);
            encoder.writeBin128(_object.getId());
        }
    }
    
    protected abstract class QMFConfigInfoCommand&lt;T extends QMFObject&gt; extends QMFInfoCommand&lt;T&gt;
    {
        protected QMFConfigInfoCommand(T object, long sampleTime)
        {
            super(QMFOperation.CONFIG_INDICATION, object, sampleTime);
        }
    }
    
    protected abstract class QMFInstrumentInfoCommand&lt;T extends QMFObject&gt; extends QMFInfoCommand&lt;T&gt;
    {
        protected QMFInstrumentInfoCommand(T object, long sampleTime)
        {
            super(QMFOperation.INSTRUMENTATION_INDICATION, object, sampleTime);
        }
    }
    
    protected abstract class QMFGetQueryResponseCommand&lt;T extends QMFObject&gt; extends QMFInfoCommand&lt;T&gt;
    {
        protected QMFGetQueryResponseCommand(T object, QMFGetQueryCommand cmd, long sampleTime)
        {
            super(cmd, QMFOperation.GET_QUERY_RESPONSE, object, sampleTime);
        }
    }
    
    
</xsl:text>
    
    <xsl:apply-templates select="node()[name()='class']" mode="class"/>
    
    <xsl:apply-templates select="node()[name()='event']" mode="event"/>

    private <xsl:value-of select="$ClassName"/>()
    {
        super(SCHEMA_NAME);
        setClasses( Arrays.asList( new QMFClass[] { <xsl:value-of select="$classList"/>, <xsl:value-of select="$eventList"/> } ) );
    }
<xsl:text disable-output-escaping="yes">
    public &lt;T extends QMFClass&gt; T getQMFClassInstance(Class&lt;T&gt; clazz)
    {
        for(QMFClass c : getClasses())
        {
            if(clazz.isInstance(c))
            {
                return (T) c;
            }
        }
        return null;
    } 
</xsl:text>    
    

    public static BrokerSchema getPackage()
    {
        return PACKAGE;
    }
    
}
</xsl:template>    


<xsl:template match="node()[name()='class']" mode="class">
    <xsl:variable name="ClassName"><xsl:value-of select="@name"/>Class</xsl:variable>
    <xsl:variable name="name"><xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template></xsl:variable>
    <xsl:variable name="propertyList"><xsl:apply-templates select="node()[name()='property']" mode="propertyList"/></xsl:variable>
    <xsl:variable name="statisticList"><xsl:apply-templates select="node()[name()='statistic']" mode="statisticList"/></xsl:variable>
    <xsl:variable name="methodList"><xsl:apply-templates select="node()[name()='method']" mode="methodList"/></xsl:variable>
    
    public class <xsl:value-of select="$ClassName"/> extends QMFObjectClass<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="@name"/>Object, <xsl:value-of select="@name"/>Delegate<xsl:text disable-output-escaping="yes">&gt;</xsl:text>
    {
        <xsl:apply-templates select="node()[name()='property']" mode="property"/>
    
        <xsl:apply-templates select="node()[name()='statistic']" mode="statistic"/>
    
        <xsl:apply-templates select="node()[name()='method']" mode="method"><xsl:with-param name="qmfClass" select="@name"/></xsl:apply-templates>
    
        private <xsl:value-of select="$ClassName"/>()
        {
            super("<xsl:value-of select="$name"/>",
                 new byte[16]);

            setProperties( Arrays.asList( new QMFProperty[] { <xsl:value-of select="$propertyList"/> } ) );
            setStatistics( Arrays.asList( new QMFStatistic[] { <xsl:value-of select="$statisticList"/> } ) );
            setMethods( Arrays.asList( new QMFMethod[] { <xsl:value-of select="$methodList"/> } ) );
        }
        
        public <xsl:value-of select="@name"/>Object newInstance(final <xsl:value-of select="@name"/>Delegate delegate)
        {
            return new <xsl:value-of select="@name"/>Object(delegate);
        }
        
    }
    
    private final <xsl:value-of select="$ClassName"/> _<xsl:value-of select="$name"/>Class = new <xsl:value-of select="$ClassName"/>();
    
    public interface <xsl:value-of select="@name"/>Delegate extends QMFObject.Delegate
    {<xsl:apply-templates select="node()[name()='property']" mode="propertyGetter"/>
    <xsl:apply-templates select="node()[name()='statistic']" mode="propertyGetter"/>
    <xsl:apply-templates select="node()[name()='method']" mode="methodDefinition"><xsl:with-param name="qmfClass" select="@name"/><xsl:with-param name="delegate">Y</xsl:with-param></xsl:apply-templates>
    }
    
    public final class <xsl:value-of select="@name"/>Object extends QMFObject<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="$ClassName"/>, <xsl:value-of select="@name"/>Delegate<xsl:text disable-output-escaping="yes">&gt;</xsl:text>
    {
        protected <xsl:value-of select="@name"/>Object(<xsl:value-of select="@name"/>Delegate delegate)
        {
           super(delegate);
        }
        
        public <xsl:value-of select="@name"/>Class getQMFClass()
        {
            return _<xsl:value-of select="$name"/>Class;
        }
        
        public QMFCommand asConfigInfoCmd(long sampleTime) 
        {
            return new QMF<xsl:value-of select="@name"/>ConfigInfoCommand(this,sampleTime);
        }
        
        public QMFCommand asInstrumentInfoCmd(long sampleTime) 
        {
           return new QMF<xsl:value-of select="@name"/>InstrumentInfoCommand(this,sampleTime);
        }
        
        public QMFCommand asGetQueryResponseCmd(QMFGetQueryCommand queryCommand, long sampleTime) 
        {
            return new QMF<xsl:value-of select="@name"/>GetQueryResponseCommand(this,queryCommand,sampleTime);
        }
    
    
        <xsl:apply-templates select="node()[name()='method']" mode="methodDefinition"><xsl:with-param name="qmfClass" select="@name"/></xsl:apply-templates>
        
        <xsl:apply-templates select="node()[name()='property']" mode="propertyDelegation"/>
        <xsl:apply-templates select="node()[name()='statistic']" mode="propertyDelegation"/>
    }

    public final class QMF<xsl:value-of select="@name"/>ConfigInfoCommand extends QMFConfigInfoCommand<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="@name"/>Object<xsl:text disable-output-escaping="yes">&gt;</xsl:text>
    {
        
        protected QMF<xsl:value-of select="@name"/>ConfigInfoCommand(<xsl:value-of select="@name"/>Object object, long sampleTime)
        {
            super(object, sampleTime);
        }
        
        @Override
        public void encode(final BBEncoder encoder)
        {
            super.encode(encoder);
            <xsl:apply-templates select="node()[name()='property']" mode="optionalPropertyPresence"/>
            <xsl:apply-templates select="node()[name()='property']" mode="encodeProperty"/>
        }
    }
    
    public final class QMF<xsl:value-of select="@name"/>InstrumentInfoCommand extends QMFInstrumentInfoCommand<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="@name"/>Object<xsl:text disable-output-escaping="yes">&gt;</xsl:text>
    {
    
        protected QMF<xsl:value-of select="@name"/>InstrumentInfoCommand(<xsl:value-of select="@name"/>Object object, long sampleTime)
        {
            super(object, sampleTime);
        }
    
        @Override
        public void encode(final BBEncoder encoder)
        {
            super.encode(encoder);
            <xsl:apply-templates select="node()[name()='statistic']" mode="optionalPropertyPresence"><xsl:with-param name="type">statistic</xsl:with-param></xsl:apply-templates>
            <xsl:apply-templates select="node()[name()='statistic']" mode="encodeProperty"/>
        }
    }
    
    public final class QMF<xsl:value-of select="@name"/>GetQueryResponseCommand extends QMFGetQueryResponseCommand<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="@name"/>Object<xsl:text disable-output-escaping="yes">&gt;</xsl:text>
    {
    
        protected QMF<xsl:value-of select="@name"/>GetQueryResponseCommand(<xsl:value-of select="@name"/>Object object, QMFGetQueryCommand cmd, long sampleTime)
        {
            super(object, cmd, sampleTime);
        }
    
        @Override
        public void encode(final BBEncoder encoder)
        {
            super.encode(encoder);
            <xsl:apply-templates select="node()[name()='property' or name()='statistic']" mode="optionalPropertyPresence"/>
            <xsl:apply-templates select="node()[name()='property' or name()='statistic']" mode="encodeProperty"/>
        }
    }
    
    
    

</xsl:template>

    <xsl:template match="node()[attribute::optional]" mode="optionalPropertyPresence">
        <xsl:param name="type">property</xsl:param>
        <xsl:variable name="var">presence<xsl:value-of select="floor(count(preceding-sibling::node()[name()=$type and attribute::optional]) div 8)"/></xsl:variable>
        <xsl:if test="count(preceding-sibling::node()[name()=$type and attribute::optional]) mod 8 = 0">
            byte <xsl:value-of select="$var"/> = (byte) 0;              
        </xsl:if>
            if(  getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>() != null )
            {
                <xsl:value-of select="$var"/> |= (1 <xsl:text disable-output-escaping="yes">&lt;&lt;</xsl:text> <xsl:value-of select="count(preceding-sibling::node()[name()=$type and attribute::optional])"/>);
            }
        <xsl:if test="count(preceding-sibling::node()[name()=$type and attribute::optional]) mod 8 = 7 or count(following-sibling::node()[name()=$type and attribute::optional]) = 0">
            encoder.writeUint8( <xsl:value-of select="$var"/> );
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="node()" mode="propertyGetter"><xsl:text>
        </xsl:text><xsl:choose>
        
        <xsl:when test="@type='hilo32'">
            <xsl:call-template name="javaType"><xsl:with-param name="type">count32</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>();<xsl:text>
        </xsl:text><xsl:call-template name="javaType"><xsl:with-param name="type">count32</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>High();<xsl:text>
        </xsl:text><xsl:call-template name="javaType"><xsl:with-param name="type">count32</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Low();</xsl:when>

        <xsl:when test="@type='mmaTime'">
            <xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Samples();<xsl:text>
        </xsl:text><xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Min();<xsl:text>
        </xsl:text><xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Max();<xsl:text>
        </xsl:text><xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Average();</xsl:when>
        
        <xsl:otherwise><xsl:call-template name="javaType"><xsl:with-param name="type" select="@type"/><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>();</xsl:otherwise>
    </xsl:choose></xsl:template>
    

    <xsl:template match="node()" mode="propertyDelegation"><xsl:text>
        </xsl:text><xsl:choose>
            <xsl:when test="@type='hilo32'">
                public <xsl:call-template name="javaType"><xsl:with-param name="type">count32</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>()
                {
                    return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>();
                }
                
                public <xsl:call-template name="javaType"><xsl:with-param name="type">count32</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>High</xsl:with-param></xsl:call-template>()
                {
                    return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>High</xsl:with-param></xsl:call-template>();
                }
                
                public <xsl:call-template name="javaType"><xsl:with-param name="type">count32</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Low</xsl:with-param></xsl:call-template>()
                {
                    return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Low</xsl:with-param></xsl:call-template>();
                }
                
            </xsl:when>
            <xsl:when test="@type='mmaTime'">
                public <xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Samples</xsl:with-param></xsl:call-template>()
                {
                    return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Samples</xsl:with-param></xsl:call-template>();
                }
                
                public <xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Min</xsl:with-param></xsl:call-template>()
                {
                    return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Min</xsl:with-param></xsl:call-template>();
                }
                
                public <xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Max</xsl:with-param></xsl:call-template>()
                {
                    return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Max</xsl:with-param></xsl:call-template>();
                }
                
                public <xsl:call-template name="javaType"><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Average</xsl:with-param></xsl:call-template>()
                {
                    return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/>Average</xsl:with-param></xsl:call-template>();
                }
                
            </xsl:when>
            <xsl:otherwise>
            public <xsl:call-template name="javaType"><xsl:with-param name="type" select="@type"/><xsl:with-param name="referenceType" select="@references"/></xsl:call-template> get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>()
            {
                return getDelegate().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>();
            }
            
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="node()" mode="encodeProperty">
        <xsl:variable name="prop">getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>()</xsl:variable>
        <xsl:choose>
            <xsl:when test="@optional">
            if(<xsl:value-of select="$prop"/> != null)
            {
                encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var" select="$prop"/><xsl:with-param name="type" select="@type"/></xsl:call-template>;
            }
            </xsl:when>
            <xsl:otherwise>
                <xsl:choose>
                    <xsl:when test="@type='hilo32'"><xsl:text>
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var" select="$prop"/><xsl:with-param name="type">count32</xsl:with-param></xsl:call-template>;<xsl:text>
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var">getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>High()</xsl:with-param><xsl:with-param name="type">count32</xsl:with-param></xsl:call-template>;<xsl:text>
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var">getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Low()</xsl:with-param><xsl:with-param name="type">count32</xsl:with-param></xsl:call-template>;
                    </xsl:when>
                
                    <xsl:when test="@type='mmaTime'"><xsl:text>            
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var">getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Samples()</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param></xsl:call-template>;<xsl:text>
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var">getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Min()</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param></xsl:call-template>;<xsl:text>
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var">getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Max()</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param></xsl:call-template>;<xsl:text>
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var">getObject().get<xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Average()</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param></xsl:call-template>;                        
                    </xsl:when>

                    <xsl:otherwise><xsl:text>
            </xsl:text>encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="var" select="$prop"/><xsl:with-param name="type" select="@type"/></xsl:call-template>;
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
        

    </xsl:template>

    <xsl:template match="node()[name()='property']" mode="property">
        <xsl:variable name="ClassName"><xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Property</xsl:variable>
        public class <xsl:value-of select="$ClassName"/> extends QMFProperty
        {
            
            private <xsl:value-of select="$ClassName"/>()
            {
                super( "<xsl:value-of select="@name"/>",
                       QMFType.<xsl:call-template name="qmfType"><xsl:with-param name="type" select="@type"></xsl:with-param></xsl:call-template>,
                       QMFProperty.AccessCode.<xsl:call-template name="toUpper"><xsl:with-param name="input" select="@access"></xsl:with-param></xsl:call-template>,
                       <xsl:choose><xsl:when test="@index='y'">true</xsl:when><xsl:otherwise>false</xsl:otherwise></xsl:choose>,
                       <xsl:choose><xsl:when test="@optional='y'">true</xsl:when><xsl:otherwise>false</xsl:otherwise></xsl:choose>);
<xsl:if test="@desc">
                setDescription("<xsl:value-of select="@desc"/>");
</xsl:if>
<xsl:if test="@min">
                setMin(<xsl:value-of select="@min"/>);
</xsl:if>
<xsl:if test="@max">
                setMin(<xsl:value-of select="@max"/>);
</xsl:if>
<xsl:if test="@references">
                setReferencedClass("<xsl:call-template name="initLower"><xsl:with-param name="input"><xsl:value-of select="@references"/></xsl:with-param></xsl:call-template>");
</xsl:if>
            
        <xsl:if test="@unit">
                setUnit("<xsl:value-of select="@unit"/>");
        </xsl:if>
            }     
        }
    
        private final <xsl:value-of select="$ClassName"/> _<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>Property = new <xsl:value-of select="$ClassName"/>();
    </xsl:template>

    <xsl:template match="node()[name()='statistic']" mode="statistic">
        <xsl:choose>
            <xsl:when test="@type='hilo32'">            
                <xsl:call-template name="statdef"><xsl:with-param name="name" select="@name"/><xsl:with-param name="type">uint32</xsl:with-param><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc" select="@desc"/></xsl:call-template>
                <xsl:call-template name="statdef"><xsl:with-param name="name"><xsl:value-of select="@name"/>High</xsl:with-param><xsl:with-param name="type">uint32</xsl:with-param><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc"><xsl:value-of select="@desc"/> (High)</xsl:with-param></xsl:call-template>
                <xsl:call-template name="statdef"><xsl:with-param name="name"><xsl:value-of select="@name"/>Low</xsl:with-param><xsl:with-param name="type">uint32</xsl:with-param><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc"><xsl:value-of select="@desc"/> (Low)</xsl:with-param></xsl:call-template>
            </xsl:when>
            <xsl:when test="@type='mmaTime'">                            
                <xsl:call-template name="statdef"><xsl:with-param name="name"><xsl:value-of select="@name"/>Samples</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc"><xsl:value-of select="@desc"/> (Samples)</xsl:with-param></xsl:call-template>
                <xsl:call-template name="statdef"><xsl:with-param name="name"><xsl:value-of select="@name"/>Max</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc"><xsl:value-of select="@desc"/> (Max)</xsl:with-param></xsl:call-template>
                <xsl:call-template name="statdef"><xsl:with-param name="name"><xsl:value-of select="@name"/>Min</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc"><xsl:value-of select="@desc"/> (Min)</xsl:with-param></xsl:call-template>
                <xsl:call-template name="statdef"><xsl:with-param name="name"><xsl:value-of select="@name"/>Average</xsl:with-param><xsl:with-param name="type">deltaTime</xsl:with-param><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc"><xsl:value-of select="@desc"/> (Average)</xsl:with-param></xsl:call-template>
            </xsl:when>
            
            <xsl:otherwise><xsl:call-template name="statdef"><xsl:with-param name="name" select="@name"/><xsl:with-param name="type" select="@type"/><xsl:with-param name="unit" select="@unit"/><xsl:with-param name="desc" select="@desc"/></xsl:call-template></xsl:otherwise>
        </xsl:choose>
        
    </xsl:template>

    <xsl:template name="statdef">
        <xsl:param name="name"/>
        <xsl:param name="type"/>
        <xsl:param name="unit"/>
        <xsl:param name="desc"/>
        <xsl:variable name="ClassName"><xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="$name"/></xsl:with-param></xsl:call-template>Statistic</xsl:variable>
        public class <xsl:value-of select="$ClassName"/> extends QMFStatistic
        {
        
            private <xsl:value-of select="$ClassName"/>()
            {
                super( "<xsl:value-of select="$name"/>", QMFType.<xsl:call-template name="qmfType"><xsl:with-param name="type" select="$type"></xsl:with-param></xsl:call-template>, <xsl:choose>
                    <xsl:when test="$unit">"<xsl:value-of select="$unit"/>"</xsl:when>
                    <xsl:otherwise>null</xsl:otherwise>
                </xsl:choose>, <xsl:choose>
                    <xsl:when test="$desc">"<xsl:value-of select="$desc"/>"</xsl:when>
                    <xsl:otherwise>null</xsl:otherwise>
                </xsl:choose>); 
            }     
        }
        
        private final <xsl:value-of select="$ClassName"/> _<xsl:call-template name="initLower"><xsl:with-param name="input" select="$name"/></xsl:call-template>Statistic = new <xsl:value-of select="$ClassName"/>();
    </xsl:template>
    

    <xsl:template match="node()[name()='method']" mode="method">
        <xsl:param name="qmfClass"/>
        <xsl:variable name="ClassName"><xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Method</xsl:variable>
        public class <xsl:value-of select="$ClassName"/> extends QMFMethod<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="$qmfClass"/>Object<xsl:text disable-output-escaping="yes">&gt;</xsl:text>
        {
            private <xsl:value-of select="$ClassName"/>()
            {
                super( "<xsl:value-of select="@name"/>", <xsl:choose>
                <xsl:when test="@desc">"<xsl:value-of select="@desc"/>"</xsl:when>
                <xsl:otherwise>null</xsl:otherwise>
</xsl:choose>);
        
                <xsl:apply-templates select="node()[name()='arg']" mode="argument"/>
        
            }
            
            
            public <xsl:value-of select="$ClassName"/>Invocation parse(BBDecoder decoder)
            {
                <xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="decodeArg"/>
                return new <xsl:value-of select="$ClassName"/>Invocation(<xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="methodArgList"/>);
            }
        }
        
        private final <xsl:value-of select="$ClassName"/> _<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>Method = new <xsl:value-of select="$ClassName"/>();
        
        private class <xsl:value-of select="$ClassName"/>Invocation implements QMFMethodInvocation<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="$qmfClass"/>Object<xsl:text disable-output-escaping="yes">&gt;</xsl:text>
        {
            <xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="methodInputArgDecl"/>
        
            private <xsl:value-of select="$ClassName"/>Invocation(<xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="methodArgList"><xsl:with-param name="includeType">yes</xsl:with-param></xsl:apply-templates>)
            {
                <xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="methodInputArgAssign"/>
            }
        
            public QMFMethodResponseCommand execute(<xsl:value-of select="$qmfClass"/>Object obj, QMFMethodRequestCommand cmd)
            {
                return obj.<xsl:value-of select="@name"/>( new <xsl:value-of select="$ClassName"/>ResponseCommandFactory(cmd)<xsl:if test="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]">, </xsl:if><xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="methodArgList"><xsl:with-param name="prefix">_</xsl:with-param></xsl:apply-templates> );
            }
        }
        
        public final class <xsl:value-of select="$ClassName"/>ResponseCommandFactory
        {
            private final QMFMethodRequestCommand _requestCmd;
            private <xsl:value-of select="$ClassName"/>ResponseCommandFactory(QMFMethodRequestCommand cmd)
            {
                _requestCmd = cmd;
            }
            
            public <xsl:value-of select="$ClassName"/>ResponseCommand createResponseCommand(CompletionCode status)
            {
                return new <xsl:value-of select="$ClassName"/>ResponseCommand(_requestCmd, status, null);
            }
            
            public <xsl:value-of select="$ClassName"/>ResponseCommand createResponseCommand(CompletionCode status, String msg)
            {
                return new <xsl:value-of select="$ClassName"/>ResponseCommand(_requestCmd, status, msg);
            }
            
            public <xsl:value-of select="$ClassName"/>ResponseCommand createResponseCommand( <xsl:apply-templates select="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]" mode="methodArgList"><xsl:with-param name="includeType">yes</xsl:with-param><xsl:with-param name="direction">O</xsl:with-param></xsl:apply-templates> )
            {
                return new <xsl:value-of select="$ClassName"/>ResponseCommand(_requestCmd<xsl:if test="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]">, <xsl:apply-templates select="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]" mode="methodArgList"><xsl:with-param name="direction">O</xsl:with-param></xsl:apply-templates></xsl:if>);
            }
        }
        
        public final class <xsl:value-of select="$ClassName"/>ResponseCommand extends QMFMethodResponseCommand
        {
            <xsl:apply-templates select="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]" mode="methodInputArgDecl"/>
            private <xsl:value-of select="$ClassName"/>ResponseCommand(QMFMethodRequestCommand cmd<xsl:if test="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]">, <xsl:apply-templates select="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]" mode="methodArgList"><xsl:with-param name="includeType">yes</xsl:with-param><xsl:with-param name="direction">O</xsl:with-param></xsl:apply-templates></xsl:if>)
            {
                super(cmd, CompletionCode.OK, "OK");
                
                <xsl:apply-templates select="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]" mode="methodInputArgAssign"/>
            }
            
            private <xsl:value-of select="$ClassName"/>ResponseCommand(QMFMethodRequestCommand cmd, CompletionCode status, String msg)
            {
                super(cmd, status, msg);
                
                <xsl:apply-templates select="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]" mode="methodInputArgAssignNull"/>
            }
            
            @Override
            public void encode(final BBEncoder encoder)
            {
                super.encode(encoder);
                
                <xsl:if test="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]">
                if(getStatus().equals(CompletionCode.OK))
                {
                    <xsl:apply-templates select="node()[name()='arg' and ( @dir='O' or @dir='IO' ) ]" mode="encodeArg"/>
                }
                </xsl:if>
            }
        }
        
    </xsl:template>
    <xsl:template match="node()[name()='method']" mode="methodDefinition"><xsl:param name="qmfClass"/><xsl:param name="delegate">N</xsl:param>
        <xsl:variable name="ClassName"><xsl:call-template name="initCap"><xsl:with-param name="input"><xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>Method</xsl:variable><xsl:text>
        </xsl:text><xsl:if test="$delegate='N'">public </xsl:if><xsl:value-of select="$qmfClass"/>Class.<xsl:value-of select="$ClassName"/>ResponseCommand <xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>(<xsl:value-of select="$qmfClass"/>Class.<xsl:value-of select="$ClassName"/>ResponseCommandFactory factory<xsl:if test="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]">, <xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="methodArgList"><xsl:with-param name="includeType">yes</xsl:with-param></xsl:apply-templates> </xsl:if>)<xsl:if test="$delegate='N'">
        {
            return getDelegate().<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>(factory<xsl:if test="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]">, <xsl:apply-templates select="node()[name()='arg' and ( @dir='I' or @dir='IO' ) ]" mode="methodArgList"/></xsl:if> );
        }
        </xsl:if><xsl:if test="$delegate='Y'">;</xsl:if>
    </xsl:template>
    <xsl:template match="node()[name()='arg']" mode="argument">
                QMFMethod.Argument <xsl:value-of select="@name"/> = new QMFMethod.Argument("<xsl:value-of select="@name"/>", QMFType.<xsl:call-template name="qmfType"><xsl:with-param name="type" select="@type"/></xsl:call-template>);
<xsl:if test="@desc">
    <xsl:text>                </xsl:text>    
    <xsl:value-of select="@name"/>.setDescription("<xsl:value-of select="@desc" disable-output-escaping="yes"/>"); 
</xsl:if>
<xsl:if test="@dir">
    <xsl:text>                </xsl:text>
    <xsl:value-of select="@name"/>.setDirection(QMFMethod.Direction.<xsl:value-of select="@dir"/>); </xsl:if>                 
                addArgument( <xsl:value-of select="@name"/> );
    </xsl:template>
    <xsl:template match="node()[name()='arg']" mode="decodeArg">
        <xsl:call-template name="javaType"><xsl:with-param name="type" select="@type"></xsl:with-param></xsl:call-template><xsl:text> </xsl:text><xsl:value-of select="@name"/> = decoder.<xsl:call-template name="decoderRead"><xsl:with-param name="type" select="@type"/></xsl:call-template>;
    </xsl:template>
    <xsl:template match="node()[name()='arg']" mode="encodeArg">
        encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="type" select="@type"/><xsl:with-param name="var">_<xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>;
    </xsl:template>
    <xsl:template match="node()[name()='arg']" mode="methodArgList"><xsl:param name="includeType"/><xsl:param name="direction">I</xsl:param><xsl:param name="prefix"></xsl:param>
        <xsl:if test="$includeType"><xsl:call-template name="javaType"><xsl:with-param name="type" select="@type"/></xsl:call-template></xsl:if><xsl:text> </xsl:text><xsl:value-of select="$prefix"/><xsl:value-of select="@name"/><xsl:if test="following-sibling::node()[name()='arg' and ( @dir=$direction or @dir='IO' ) ]">, </xsl:if>
    </xsl:template>
    <xsl:template match="node()[name()='arg']" mode="methodInputArgDecl">
            private final <xsl:call-template name="javaType"><xsl:with-param name="type" select="@type"/></xsl:call-template> _<xsl:value-of select="@name"/>;
    </xsl:template>
    <xsl:template match="node()[name()='arg']" mode="methodInputArgAssign">
                _<xsl:value-of select="@name"/> = <xsl:value-of select="@name"/>;
    </xsl:template>
    <xsl:template match="node()[name()='arg']" mode="methodInputArgAssignNull">
                _<xsl:value-of select="@name"/> = null;
    </xsl:template>
    
    
    <xsl:template match="node()[name()='class']" mode="classList">_<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>Class<xsl:if test="following-sibling::node()[name()='class']">, </xsl:if></xsl:template>
    <xsl:template match="node()[name()='event']" mode="eventList">_<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>EventClass<xsl:if test="following-sibling::node()[name()='event']">, </xsl:if></xsl:template>
    <xsl:template match="node()[name()='property']" mode="propertyList">_<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>Property<xsl:if test="following-sibling::node()[name()='property']">, </xsl:if></xsl:template>
    <xsl:template match="node()[name()='statistic']" mode="statisticList"><xsl:variable name="field">_<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template></xsl:variable><xsl:choose><xsl:when test="@type!='mmaTime'"><xsl:value-of select="$field"/>Statistic<xsl:if test="@type='hilo32'">, <xsl:value-of select="$field"/>HighStatistic, <xsl:value-of select="$field"/>LowStatistic</xsl:if></xsl:when><xsl:otherwise><xsl:value-of select="$field"/>SamplesStatistic, <xsl:value-of select="$field"/>MaxStatistic, <xsl:value-of select="$field"/>MinStatistic, <xsl:value-of select="$field"/>AverageStatistic</xsl:otherwise></xsl:choose><xsl:if test="following-sibling::node()[name()='statistic']">, </xsl:if></xsl:template>
    <xsl:template match="node()[name()='method']" mode="methodList">_<xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template>Method<xsl:if test="following-sibling::node()[name()='method']">, </xsl:if></xsl:template>
    

    <xsl:template match="node()[name()='event']" mode="event">
        <xsl:variable name="ClassName"><xsl:call-template name="initCap"><xsl:with-param name="input" select="@name"/></xsl:call-template>EventClass</xsl:variable>
        <xsl:variable name="cmdName"><xsl:call-template name="initCap"><xsl:with-param name="input" select="@name"/></xsl:call-template>Event</xsl:variable>
        <xsl:variable name="name"><xsl:call-template name="initLower"><xsl:with-param name="input" select="@name"/></xsl:call-template></xsl:variable>

        <xsl:variable name="argsList"><xsl:call-template name="argList"><xsl:with-param name="args" select="@args"></xsl:with-param></xsl:call-template></xsl:variable>
        
    public class <xsl:value-of select="$ClassName"/> extends QMFEventClass
    {
<!--        <xsl:apply-templates select="node()[name()='property']" mode="property"/> -->
        
        <xsl:apply-templates select="preceding-sibling::node()[name()='eventArguments']" mode="eventArg"><xsl:with-param name="args" select="@args"/></xsl:apply-templates>
        
        private <xsl:value-of select="$ClassName"/>()
        {
            super("<xsl:value-of select="$name"/>",
            new byte[16]);
           
            setProperties( Arrays.asList( new QMFProperty[] { <xsl:value-of select="$argsList"/> } ) );
        }
        
        public QMFEventSeverity getSeverity()
        {
            return QMFEventSeverity.<xsl:call-template name="severity"><xsl:with-param name="severity" select="@sev"/></xsl:call-template>;
        }
        
        public QMFEventCommand<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="$ClassName"/><xsl:text disable-output-escaping="yes">&gt;</xsl:text> newEvent(<xsl:apply-templates select="preceding-sibling::node()[name()='eventArguments']" mode="eventArg"><xsl:with-param name="args" select="@args"/><xsl:with-param name="tmpl">argParamList</xsl:with-param><xsl:with-param name="separator">, </xsl:with-param></xsl:apply-templates>)
        {
            return new  <xsl:value-of select="$cmdName"/>(<xsl:apply-templates select="preceding-sibling::node()[name()='eventArguments']" mode="eventArg"><xsl:with-param name="args" select="@args"/><xsl:with-param name="tmpl">argList</xsl:with-param><xsl:with-param name="separator">, </xsl:with-param></xsl:apply-templates>);
        }
        
        
        
    }
        
    private final <xsl:value-of select="$ClassName"/> _<xsl:value-of select="$name"/>EventClass = new <xsl:value-of select="$ClassName"/>();
        
    private final class <xsl:value-of select="$cmdName"/> extends QMFEventCommand<xsl:text disable-output-escaping="yes">&lt;</xsl:text><xsl:value-of select="$ClassName"/><xsl:text disable-output-escaping="yes">&gt;</xsl:text>
    {
        <xsl:apply-templates select="preceding-sibling::node()[name()='eventArguments']" mode="eventArg"><xsl:with-param name="args" select="@args"/><xsl:with-param name="tmpl">argMemberDef</xsl:with-param></xsl:apply-templates>
        
        private <xsl:value-of select="$cmdName"/>(<xsl:apply-templates select="preceding-sibling::node()[name()='eventArguments']" mode="eventArg"><xsl:with-param name="args" select="@args"/><xsl:with-param name="tmpl">argParamList</xsl:with-param><xsl:with-param name="separator">, </xsl:with-param></xsl:apply-templates>)
        {
        <xsl:apply-templates select="preceding-sibling::node()[name()='eventArguments']" mode="eventArg"><xsl:with-param name="args" select="@args"/><xsl:with-param name="tmpl">argMemberAssign</xsl:with-param></xsl:apply-templates>        
        }
        
        public <xsl:value-of select="$ClassName"/> getEventClass()
        {
            return _<xsl:value-of select="$name"/>EventClass;
        }
        
        public void encode(final BBEncoder encoder)
        {
            super.encode(encoder);
            <xsl:apply-templates select="preceding-sibling::node()[name()='eventArguments']" mode="eventArg"><xsl:with-param name="args" select="@args"/><xsl:with-param name="tmpl">argEncode</xsl:with-param><xsl:with-param name="separator"><xsl:text>
            </xsl:text></xsl:with-param></xsl:apply-templates>        
        
        }
    }
    </xsl:template>
    
    <xsl:template name="eventArguments" mode="eventArg" match="node()[name()='eventArguments']">        
        <xsl:param name="args"></xsl:param>
        <xsl:param name="tmpl">propertyClass</xsl:param>
        <xsl:param name="separator"></xsl:param>
        <xsl:variable name="arg"><xsl:choose>
            <xsl:when test="contains($args,',')"><xsl:value-of select="normalize-space(substring-before($args,','))"/></xsl:when>
            <xsl:otherwise><xsl:value-of select="$args"/></xsl:otherwise>
        </xsl:choose></xsl:variable>
        <xsl:variable name="tail" select="normalize-space(substring-after($args,','))"></xsl:variable>
        <xsl:if test="string-length($arg)>0">
            <xsl:apply-templates mode="eventArg" select="node()[name()='arg' and @name=$arg]"><xsl:with-param name="tmpl" select="$tmpl"/></xsl:apply-templates> 
        </xsl:if>
        <xsl:if test="string-length($tail)>0"><xsl:value-of select="$separator"/><xsl:apply-templates select="." mode="eventArg"><xsl:with-param name="args" select="$tail"/><xsl:with-param name="tmpl" select="$tmpl"/><xsl:with-param name="separator" select="$separator"/></xsl:apply-templates></xsl:if>        
    </xsl:template>
    
    <xsl:template mode="eventArg" match="node()[name()='arg']">
        <xsl:param name="tmpl"/>
        <xsl:choose>
            <xsl:when test="$tmpl='propertyClass'"><xsl:apply-templates mode="propertyClass" select="."/></xsl:when>
        </xsl:choose>
        <xsl:choose>
            <xsl:when test="$tmpl='argParamList'"><xsl:apply-templates mode="argParamList" select="."/></xsl:when>
        </xsl:choose>
        <xsl:choose>
            <xsl:when test="$tmpl='argList'"><xsl:apply-templates mode="argList" select="."/></xsl:when>
        </xsl:choose>
        <xsl:choose>
            <xsl:when test="$tmpl='argMemberDef'"><xsl:apply-templates mode="argMemberDef" select="."/></xsl:when>
        </xsl:choose>
        <xsl:choose>
            <xsl:when test="$tmpl='argMemberAssign'"><xsl:apply-templates mode="argMemberAssign" select="."/></xsl:when>
        </xsl:choose>
        <xsl:choose>
            <xsl:when test="$tmpl='argEncode'"><xsl:apply-templates mode="argEncode" select="."/></xsl:when>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template name="arg" mode="propertyClass" match="node()[name()='arg']">
<xsl:variable name="propClassName"><xsl:call-template name="initCap"><xsl:with-param name="input" select="@name"/></xsl:call-template>Arg</xsl:variable>        
        public class <xsl:value-of select="$propClassName"/> extends QMFProperty
        {
            private  <xsl:value-of select="$propClassName"/>()
            {
                super( "<xsl:value-of select="@name"/>",
                       QMFType.<xsl:call-template name="qmfType"><xsl:with-param name="type" select="@type"></xsl:with-param></xsl:call-template>,
                       QMFProperty.AccessCode.RO,false,false);
                
                <xsl:if test="@desc">
                setDescription("<xsl:value-of select="@desc"/>");    
                </xsl:if>
            }
        }
        
        private final <xsl:value-of select="$propClassName"/> _<xsl:value-of select="@name"/>Arg = new <xsl:value-of select="$propClassName"/>();
    </xsl:template>
    
    <xsl:template mode="argMemberDef" match="node()[name()='arg']"><xsl:text>
        private final </xsl:text><xsl:call-template name="javaType"><xsl:with-param name="type" select="@type"></xsl:with-param></xsl:call-template><xsl:text> _</xsl:text><xsl:value-of select="@name"/>;</xsl:template>
    <xsl:template mode="argParamList" match="node()[name()='arg']"><xsl:call-template name="javaType"><xsl:with-param name="type" select="@type"/></xsl:call-template><xsl:text> </xsl:text><xsl:value-of select="@name"/></xsl:template>
    <xsl:template mode="argList" match="node()[name()='arg']"><xsl:value-of select="@name"/></xsl:template>
    <xsl:template mode="argMemberAssign" match="node()[name()='arg']"><xsl:text>
            _</xsl:text><xsl:value-of select="@name"/> = <xsl:value-of select="@name"/>;</xsl:template>
    <xsl:template mode="argEncode" match="node()[name()='arg']">encoder.<xsl:call-template name="encoderWrite"><xsl:with-param name="type" select="@type"/><xsl:with-param name="var">_<xsl:value-of select="@name"/></xsl:with-param></xsl:call-template>;</xsl:template>
    
    <xsl:template name="argList">
        <xsl:param name="args"/>
        <xsl:variable name="arg"><xsl:choose>
            <xsl:when test="contains($args,',')"><xsl:value-of select="normalize-space(substring-before($args,','))"/></xsl:when>
            <xsl:otherwise><xsl:value-of select="$args"/></xsl:otherwise>
        </xsl:choose></xsl:variable>        
        <xsl:variable name="tail" select="normalize-space(substring-after($args,','))"></xsl:variable>
        <xsl:if test="string-length($arg)>0">_<xsl:value-of select="$arg"/>Arg</xsl:if>
        <xsl:if test="string-length($tail)>0">, <xsl:call-template name="argList"><xsl:with-param name="args" select="$tail"/></xsl:call-template></xsl:if>
    </xsl:template>


    <xsl:template name="qmfType"><xsl:param name="type"/>
<xsl:choose><xsl:when test="$type='absTime'">ABSTIME</xsl:when>
<xsl:when test="$type='bool'">BOOLEAN</xsl:when>
<xsl:when test="$type='map'">MAP</xsl:when>
<xsl:when test="$type='objId'">OBJECTREFERENCE</xsl:when>
<xsl:when test="$type='sstr'">STR8</xsl:when>
<xsl:when test="$type='lstr'">STR16</xsl:when>
<xsl:when test="$type='uint16'">UINT16</xsl:when>
<xsl:when test="$type='uint32'">UINT32</xsl:when>
<xsl:when test="$type='uint64'">UINT64</xsl:when>
<xsl:when test="$type='uuid'">UUID</xsl:when>
<xsl:when test="$type='deltaTime'">DELTATIME</xsl:when>    
<xsl:when test="$type='count32'">UINT32</xsl:when>
<xsl:when test="$type='count64'">UINT64</xsl:when>    
    <xsl:otherwise><xsl:value-of select="$type"/></xsl:otherwise>
</xsl:choose>
</xsl:template>
        

    <xsl:template name="javaType"><xsl:param name="type"/><xsl:param name="referenceType">UUID</xsl:param>
        <xsl:choose><xsl:when test="$type='absTime'">Long</xsl:when>
            <xsl:when test="$type='bool'">Boolean</xsl:when>
            <xsl:when test="$type='map'">Map</xsl:when>
            <xsl:when test="$type='objId'"><xsl:value-of select="$referenceType"/>Object</xsl:when>
            <xsl:when test="$type='sstr'">String</xsl:when>
            <xsl:when test="$type='lstr'">String</xsl:when>
            <xsl:when test="$type='uint16'">Integer</xsl:when>
            <xsl:when test="$type='uint32'">Long</xsl:when>
            <xsl:when test="$type='uint64'">Long</xsl:when>
            <xsl:when test="$type='uuid'">UUID</xsl:when>
            <xsl:when test="$type='deltaTime'">Long</xsl:when>    
            <xsl:when test="$type='count32'">Long</xsl:when>
            <xsl:when test="$type='count64'">Long</xsl:when>    
            <xsl:otherwise><xsl:value-of select="$type"/></xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="encoderWrite"><xsl:param name="type"/><xsl:param name="var"/>
        <xsl:choose><xsl:when test="$type='absTime'">writeUint64( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='bool'">writeInt8( <xsl:value-of select="$var"/> ? (byte) -1 : (byte) 0)</xsl:when>
            <xsl:when test="$type='map'">writeMap( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='objId'">writeBin128( <xsl:value-of select="$var"/>.getId() )</xsl:when>
            <xsl:when test="$type='sstr'">writeStr8( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='lstr'">writeStr16( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='uint16'">writeUint16( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='uint32'">writeUint32( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='uint64'">writeUint64( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='uuid'">writeUuid( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='deltaTime'">writeUint64( <xsl:value-of select="$var"/> )</xsl:when>    
            <xsl:when test="$type='count32'">writeUint32( <xsl:value-of select="$var"/> )</xsl:when>
            <xsl:when test="$type='count64'">writeUint64( <xsl:value-of select="$var"/> )</xsl:when>    
            <xsl:otherwise><xsl:value-of select="$type"/></xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="decoderRead"><xsl:param name="type"/>
        <xsl:choose><xsl:when test="$type='absTime'">readUint64()</xsl:when>
            <xsl:when test="$type='bool'">readInt8() != 0</xsl:when>
            <xsl:when test="$type='map'">readMap()</xsl:when>
            <xsl:when test="$type='objId'">readBin128()</xsl:when>
            <xsl:when test="$type='sstr'">readStr8()</xsl:when>
            <xsl:when test="$type='lstr'">readStr16()</xsl:when>
            <xsl:when test="$type='uint16'">readUint16()</xsl:when>
            <xsl:when test="$type='uint32'">readUint32()</xsl:when>
            <xsl:when test="$type='uint64'">readUint64()</xsl:when>
            <xsl:when test="$type='uuid'">readUuid()</xsl:when>
            <xsl:when test="$type='deltaTime'">readUint64()</xsl:when>    
            <xsl:when test="$type='count32'">readUint32()</xsl:when>
            <xsl:when test="$type='count64'">readUint64()</xsl:when>    
            <xsl:otherwise><xsl:value-of select="$type"/></xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    

<xsl:template name="severity">
    <xsl:param name="severity"/>
    <xsl:choose>
        <xsl:when test="$severity='emerg'">EMERGENCY</xsl:when>
        <xsl:when test="$severity='alert'">ALERT</xsl:when>
        <xsl:when test="$severity='crit'">CRITICAL</xsl:when>
        <xsl:when test="$severity='error'">ERROR</xsl:when>
        <xsl:when test="$severity='warn'">WARN</xsl:when>
        <xsl:when test="$severity='notice'">NOTICE</xsl:when>
        <xsl:when test="$severity='inform'">INFORM</xsl:when>
        <xsl:when test="$severity='debug'">DEBUG</xsl:when>
    </xsl:choose>
    
</xsl:template>    

<xsl:template name="substringAfterLast"><xsl:param name="input"/><xsl:param name="arg"/>
<xsl:choose>
    <xsl:when test="contains($input,$arg)"><xsl:call-template name="substringAfterLast"><xsl:with-param name="input"><xsl:value-of select="substring-after($input,$arg)"/></xsl:with-param><xsl:with-param name="arg"><xsl:value-of select="$arg"/></xsl:with-param></xsl:call-template></xsl:when>
    <xsl:otherwise><xsl:value-of select="$input"/></xsl:otherwise>
</xsl:choose>
</xsl:template>
<xsl:template name="initCap"><xsl:param name="input"/><xsl:value-of select="translate(substring($input,1,1),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/><xsl:value-of select="substring($input,2)"/></xsl:template>
<xsl:template name="initLower"><xsl:param name="input"/><xsl:value-of select="translate(substring($input,1,1),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/><xsl:value-of select="substring($input,2)"/></xsl:template>
<xsl:template name="toUpper"><xsl:param name="input"/><xsl:value-of select="translate($input,'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/></xsl:template>
</xsl:stylesheet>
