<%--
    Copyright (C) 2013 Infinite Automation Software. All rights reserved.
    @author Terry Packer
--%>
<%@ include file="/WEB-INF/jsp/include/tech.jsp" %>

<div id="pointTableDiv" data-dojo-type="dijit/layout/ContentPane" title="Data Points" data-dojo-props="selected:true" style="overflow-y:auto" class="borderDivPadded marB" >
    <span class="smallTitle"><fmt:message key="dsEdit.points.details"/></span>
    <c:if test="${!empty pointHelpId}"><tag:help id="${pointHelpId}"/></c:if>

    
    <div id="dataPointTable"></div>

    <span class="smallTitle"><fmt:message key="common.add"/></span>
    <tag:img png="add" title="common.add" id="addDataPoint" onclick="dataPoints.open(-1)"/>    
<!-- Include the Edit Div -->
<jsp:include page="dataPointEdit.jsp"/>
</div>