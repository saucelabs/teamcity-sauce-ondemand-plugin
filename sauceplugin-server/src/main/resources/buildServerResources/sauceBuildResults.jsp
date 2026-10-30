<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>

<jsp:useBean id="build" scope="request" type="jetbrains.buildServer.serverSide.SBuild"/>
<jsp:useBean id="jobs" scope="request" type="java.util.ArrayList"/>

<c:choose>
    <c:when test="${not empty sauceError}">
        <div id="sauce-results-error" class="attentionComment">
            <c:out value="${sauceError}"/>
        </div>
    </c:when>
    <c:when test="${not empty sauceNotice}">
        <p id="sauce-results-notice">
            <c:out value="${sauceNotice}"/>
        </p>
    </c:when>
    <c:otherwise>
        <div id="sauce-job-list" class="groupBox">
            <table>
                <tr>
                    <th>Job Id</th>
                    <th>Name</th>
                    <th>Status</th>
                </tr>
                <c:forEach var="jobIter" items="${jobs}">
                    <tr>
                        <td>
                            <a href="<%=request.getAttribute("javax.servlet.forward.request_uri")%>?buildId=<%=request.getParameter("buildId")%>&buildTypeId=<%=request.getParameter("buildTypeId")%>&tab=<%=request.getParameter("tab")%>&jobId=${jobIter.jobId}&logUrl=${jobIter.logUrl}&hmac=${jobIter.hmac}">${jobIter.jobId}</a>
                        </td>
                        <td>
                                ${jobIter.name}
                        </td>
                        <td>
                                ${jobIter.status}
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </div>
    </c:otherwise>
</c:choose>

<c:choose>
    <c:when test="${param.jobId != null}">
        <div id="sauce-job" class="groupBox">
           <h2>Details for ${param.jobId}</h2>
            <script 
                type="text/javascript"
                src="${param.logUrl}/job-embed/${param.jobId}.js?auth=${param.hmac}"
            >
            </script>
        </div>
        <div>
            <script type="text/javascript">
                var iframe = document.getElementById('sauce-job').children[1];
                iframe.style.width = "1024px";
                iframe.style.height = "1000px";
            </script>
        </div>
    </c:when>
    <c:otherwise>


    </c:otherwise>
</c:choose>