<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<c:url var="actionUrl" value="/sauceSettings.html"/>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="sauceBrowsersBean" scope="request" class="com.saucelabs.ci.BrowserFactory"/>

<c:set var="selectedBrowsers"
       value="${propertiesBean.properties['saucePlugin.selectedBrowsers']}"/>

<c:set var="sauceEnabled"
       value="${propertiesBean.properties['saucePlugin.sauceEnabled']}"/>

<c:set var="displaySauceSettings"
       value="${not empty sauceEnabled ? true : false}"/>

<tr id="saucePlugin.sauceConnect.container">
    <th><label for="saucePlugin.sauceConnect">Start Sauce Connect:</label></th>
    <td>
        <props:checkboxProperty name="saucePlugin.sauceConnect" treatFalseValuesCorrectly="${true}"
                                uncheckedValue="false"/>
    </td>
</tr>

<tr id="saucePlugin.sauceConnect.container">
    <th><label for="saucePlugin.sauceConnectOptions">Sauce Connect Command Line Options:</label></th>
    <td>
        <props:textProperty name="saucePlugin.sauceConnectOptions"/>
    </td>
</tr>

<tr id="saucePlugin.userId.container">
    <th><label for="saucePlugin.userId">Sauce User:</label></th>
    <td>
        <props:textProperty name="saucePlugin.userId"/>
    </td>
</tr>

<tr id="saucePlugin.accessKey.container">
    <th><label for="saucePlugin.accessKey">Sauce Access Key:</label></th>
    <td>
        <props:textProperty name="saucePlugin.accessKey"/>
    </td>
</tr>

<tr id="saucePlugin.seleniumHost.container">
    <th><label for="saucePlugin.seleniumHost">Selenium Host:</label></th>
    <td>
        <props:textProperty name="saucePlugin.seleniumHost"/>
    </td>
</tr>

<tr id="saucePlugin.seleniumPort.container">
    <th><label for="saucePlugin.seleniumPort">Selenium Port:</label></th>
    <td>
        <props:textProperty name="saucePlugin.seleniumPort"/>
    </td>
</tr>

<tr id="saucePlugin.dataCenter.container">
    <th><label for="saucePlugin.dataCenter">Data Center:</label></th>
    <td>
        <props:textProperty name="saucePlugin.dataCenter" />
    </td>
</tr>

<tr id="saucePlugin.webDriverBrowsers.container">
    <th><label for="saucePlugin.webDriverBrowsers">Browsers:</label></th>
    <td>
        <props:hiddenProperty name="saucePlugin.selectedBrowsers" value=""/>
        <props:selectProperty name="saucePlugin.webDriverBrowsers" multiple="true" style="height: 160px;"
                              onclick="
                              var selectedBrowsers = '';
                                  for (x = 0; x < document.getElementById('saucePlugin.webDriverBrowsers').length; x++) {
                                      if (document.getElementById('saucePlugin.webDriverBrowsers')[x].selected) {

                                          selectedBrowsers = document.getElementById('saucePlugin.webDriverBrowsers')[x].value + ',' + selectedBrowsers;
                                      }
                                  }
                                  document.getElementById('saucePlugin.selectedBrowsers').value = selectedBrowsers;">

            <c:forEach var="browser" items="${sauceBrowsersBean.webDriverBrowsers}">
                <c:set var="selected" value="false"/>
                <c:if test="${selectedBrowsers.contains(browser.key)}">
                    <c:set var="selected" value="true"/>
                </c:if>
                <props:option value="${browser.key}"
                              selected="${selected}"><c:out value="${browser.name}"/></props:option>
            </c:forEach>
        </props:selectProperty>
    </td>
</tr>

<tr id="saucePlugin.embedResults.container">
    <th><label for="saucePlugin.disableResults">Disable Embedded Sauce Results:</label></th>
    <td>
        <props:checkboxProperty name="saucePlugin.disableResults" treatFalseValuesCorrectly="${true}"
                                uncheckedValue="false"/>
    </td>
</tr>

<tr id="saucePlugin.useLatestSauceConnect.container">
    <th><label for="saucePlugin.useLatestSauceConnect">Use latest <a href="https://docs.saucelabs.com/secure-connections/sauce-connect-5/">Sauce Connect</a> (if unchecked, uses 5.2.2):</label></th>
    <td>
        <props:checkboxProperty name="saucePlugin.useLatestSauceConnect" treatFalseValuesCorrectly="${true}"
                                uncheckedValue="false"/>
    </td>
</tr>
