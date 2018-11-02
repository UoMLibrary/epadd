<%@ page import="java.io.File" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.index.ArchiveReaderWriter" %>
<%--<%@include file="getArchive.jspf" %>--%>
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Set Images</title>
    <link rel="icon" type="image/png" href="images/epadd-favicon.png">

    <link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
    <jsp:include page="css/css.jsp"/>

    <script src="js/jquery.js"></script>
    <script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>
    <script src="js/epadd.js" type="text/javascript"></script>
</head>
<body>
 <%
     String collectionID = request.getParameter("collection");
     Archive archive= JSPHelper.getArchive(request);

     //INV: here collectionID=null && archiveID="" can not be true. Exactly one should be true.
     //This page can be invoked in two different context. One: from the colleciton-detail page with collection
     //dir as an argument (in "collection") and two: from epadd-settings page with archiveID as an argument.
     //collectionID is empty if the mode is appraisal mode. Otherwise we get the basedir from archive and set collecitonid
     //as the name of the base directory.

 %>
 <%@include file="header.jspf"%>

    <script>epadd.nav_mark_active('Collections');</script>

    <div style="margin-left:170px">
        You can upload images that represent the collection here. Only PNG format files are supported.
        <p></p>
        <form method="POST" action="upload-images" enctype="multipart/form-data" >
            <%--adding a hidden input field to pass collectionID the server. This is a common pattern used to pass--%>
            <%--archiveID in all those forms where POST was used to invoke the server page.--%>
            <input type="hidden" value="<%=collectionID%>" name="collection"/>
                <input type="hidden" value="<%=archiveID%>" name="archiveID"/>

            Profile Photo: (Aspect ratio 1:1)<br/>
            <%
                String file = null;
                if(!Util.nullOrEmpty(collectionID))
                    file = collectionID + File.separator + Archive.BAG_DATA_FOLDER+ File.separator+ Archive.IMAGES_SUBDIR + File.separator + "profilePhoto.png";
                else if(!Util.nullOrEmpty(archiveID))
                    file = new File(archive.baseDir).getName() + File.separator + Archive.BAG_DATA_FOLDER+ File.separator+ Archive.IMAGES_SUBDIR + File.separator + "profilePhoto";
            %>

                <div class="profile-small-img" style="background-image:url('serveImage.jsp?file=<%=file%>')"></div>
                <%--<div class="profile-small-img" style=" background-size: contain;--%>
                        <%--background-repeat: no-repeat;--%>
                        <%--width: 50%;--%>
                        <%--height: 50%;--%>
                        <%--padding-top: 20%;  background-image:url('serveImage.jsp?file=<%=file%>')">--%>
            <input type="file" name="profilePhoto" id="profilePhoto" /> <br/><br/>

            Landing Page Photo: (Aspect ratio 4:3)<br/>
            <%
                if(!Util.nullOrEmpty(collectionID))
                    file = collectionID + File.separator + Archive.BAG_DATA_FOLDER+ File.separator+ Archive.IMAGES_SUBDIR + File.separator + "landingPhoto";
                else if(!Util.nullOrEmpty(archiveID))
                    file = new File(archive.baseDir).getName() + File.separator + Archive.BAG_DATA_FOLDER+ File.separator+  Archive.IMAGES_SUBDIR + File.separator + "landingPhoto";
            %>
            <div class="landing-img" style="background-image:url('serveImage.jsp?file=<%=file%>')"></div>
            <br/>
            <br/>
            <input type="file" name="landingPhoto" id="landingPhoto" />
            <br/><br/>

            <%
                if(!Util.nullOrEmpty(collectionID))
                    file = collectionID + File.separator + Archive.BAG_DATA_FOLDER+ File.separator+  Archive.IMAGES_SUBDIR + File.separator + "bannerImage";
                else if(!Util.nullOrEmpty(archiveID))
                    file = new File(archive.baseDir).getName() + File.separator + Archive.BAG_DATA_FOLDER+ File.separator+ Archive.IMAGES_SUBDIR + File.separator + "bannerImage";
            %>
            Banner Image: (Aspect ratio 2.5:1)<br/>
                <div class="banner-img" style=" background-size: contain;
                        background-repeat: no-repeat;
                        width: 50%;
                        height: 50%;
                        padding-top: 20%;  background-image:url('serveImage.jsp?file=<%=file%>')"> <!-- https://stackoverflow.com/questions/2643305/centering-a-background-image-using-css -->
<%--Height added in style otherwise the image was not being visible--%>
                </div>
            <br/>
            <br/>
            <input type="file" name="bannerImage" id="bannerImage" />
            <br/><br/>
            <button id="upload-btn" class="btn btn-cta">Upload <i class="icon-arrowbutton"></i></button>
        </form>
    </div>

    <jsp:include page="footer.jsp"/>

    </body>
</html>