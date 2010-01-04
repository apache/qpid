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
				<jsp:param name="title" value="TBD"/>
			</jsp:include>
				
			<div id="content" align="center">
				<jsp:include page="/fragments/menu.jsp"/>
						
				<div id="contenttext">
					<div class="panel" align="justify" style="height:500px; overflow-y:auto;">
						<span class="bodytext">
								Sorry, this feature is not yet available!
                		</span>	
            		</div>
				</div>
			</div>
		</div>
	</body>
</html>