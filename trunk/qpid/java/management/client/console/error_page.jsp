<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib uri='http://java.sun.com/jsp/jstl/core' prefix='c'%>
<html>
	<head>
		<link rel="stylesheet" href="<%=request.getContextPath()%>/images/style.css" type="text/css" />
		<title>QMan Administration Console</title>
	</head>
	<body>
		<div id="page" align="center">
			<jsp:include page="/fragments/header.jsp">
				<jsp:param name="title" value="Error Page"/>
			</jsp:include>
				
			<div id="content" align="center">
				<jsp:include page="/fragments/menu.jsp"/>
						
				<div id="contenttext">
					<div class="panel" align="justify" style="height:500px; overflow-y:auto;">
						<span class="bodytext">
							<table width="100%">
								<tr><td nowrap style="font-weight: bold;">
									We are not able to satify your request because an error has happened.
									<br>Message : ${errorMessage}
								</td></tr>
								<tr><td nowrap style="font-size: xx-small; font-weight: bold;">
									<c:forEach var="stackTrace" items="${exception.stackTrace}">	
										${stackTrace}
										<br>
									</c:forEach>
								</td></tr>									
							</table>	
                		</span>	
            		</div>
				</div>
			</div>
		</div>
	</body>
</html>