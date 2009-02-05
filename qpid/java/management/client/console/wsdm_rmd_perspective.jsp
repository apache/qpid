<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri='http://java.sun.com/jsp/jstl/core' prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/xml" prefix="x"%>

<%@page import="org.apache.qpid.management.web.action.BrokerModel"%>
<%@page import="java.util.Set"%>
<%@page import="javax.management.ObjectName"%>
<%@page import="org.apache.qpid.management.Names"%>
<%@page import="java.util.*"%>
<%@page import="java.net.URI"%>
<%@page import="javax.xml.namespace.QName"%>
<%@page import="org.w3c.dom.Element"%>
<html>
	<head>
		<link rel="stylesheet" href="<%=request.getContextPath()%>/images/style.css" type="text/css" />
		<title>QMan Administration Console</title>
	</head>
	<body>
		<div id="page" align="center">
			<jsp:include page="/fragments/header.jsp">
				<jsp:param name="title" value="Resource Management - WS-DM WSDL Perspective"/>
			</jsp:include>
				
			<div id="content" align="center">
				<jsp:include page="/fragments/menu.jsp"/>
				
			<div id="contenttext">
        	<div id="wsdmmenu" align="left">
                <ul>
                    <li><a href="<%=request.getContextPath()%>/jmx_perspective?resourceId=${resourceId}"><span>JMX</span></a></li>
                    <li><a href="<%=request.getContextPath()%>/wsdm_properties_perspective?wsresourceId=${resourceId}"><span>WS-DM</span></a></li>
                </ul>
            </div>
            <br />
			<div class="panel" align="justify">
				<span class="bodytext">
                	<table width="100%">
                    	<tr>
                        	<td valign="top" colspan="2">
                            	<fieldset>
                                	<legend>Resource ID</legend>
                                    <ul>
                                    	<c:forEach var="property" items="${nameAttributes}">
                                            	<li>
                                            		<c:out value="${property}"/>
                                            	</li>
                                          </c:forEach>      
                                     </ul>
                                </fieldset>
                            </td>
                        </tr>
						<tr>
                        	<td valign="top">
                            	<div id="wsdmmenu" align="left" style="font-size: small;">
                                    <ul>
                                        <li><a href="<%=request.getContextPath()%>/wsdm_properties_perspective?resourceId=${resourceId}"><span>Properties</span></a></li>
                                        <li><a href="<%=request.getContextPath()%>/wsdm_operations_perspective?resourceId=${resourceId}""><span>Operations</span></a></li>
                                        <li><a href="<%=request.getContextPath()%>/wsdm_wsdl_perspective?resourceId=${resourceId}""><span>WSDL</span></a></li>
                                        <li><a href="<%=request.getContextPath()%>/wsdm_rmd_perspective?resourceId=${resourceId}""><span>RDM</span></a></li>
                                    </ul>
                                </div>
                            </td>
                        </tr>                                          
                        <tr>    
                        	<td valign="top">
								<div class="panel" align="justify" style="height:500px; overflow-y:auto;">								
									<c:set var="xml">
										${wsdl} 	  									
  									</c:set>
  									<c:import var="xslt" url="wsdl-viewer.xsl" />
									<x:transform xml="${xml}" xslt="${xslt}" />
                            	</div>
                            </td>
                        </tr>
                    </table>
                </span>	
            </div>
			</div>
			</div>
		</div>
	</body>
</html>