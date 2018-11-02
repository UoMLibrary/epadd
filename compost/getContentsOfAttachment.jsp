<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%--
  Created by IntelliJ IDEA.
  User: vihari
  Date: 23/06/15
  Time: 14:31
  To change this template use File | Settings | File Templates.
  Usage: use it with fileName param, location of this jsp Webcontent
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
  <title>Attachment content</title>
  <link rel="icon" type="image/png" href="../WebContent/images/epadd-favicon.png">

  <script src="../WebContent/js/jquery.js"></script>

  <link rel="stylesheet" href="../WebContent/bootstrap/dist/css/bootstrap.min.css">
  <script type="text/javascript" src="../WebContent/bootstrap/dist/js/bootstrap.min.js"></script>

  <jsp:include page="../WebContent/css/css.jsp"/>
  <script src="../WebContent/js/muse.js"></script>
  <script src="../WebContent/js/epadd.js"></script>
</head>
<body>
<%@include file="../WebContent/header.jspf"%>
<%
  Archive archive = (Archive)session.getAttribute("archive");
  if(archive == null) {
    out.println("Archive is null! Please load the archive and reload");
    return;
  }
  String fileName = request.getParameter("fileName");
  System.err.println("received request for: fileName: "+fileName);
  out.println("<div class='main' style='padding:50px'>");
  Pair<String,String> pair = archive.getContentsOfAttachment(fileName);
  String content = pair.first;
  if (content == null){
      out.println("<span style='color:red'>"+pair.second+"</span></div>");
      return;
  }
  content = content.replaceAll("\\n","<br>");
  out.println(content);
  out.println("</div>");
%>
</body>
</html>
