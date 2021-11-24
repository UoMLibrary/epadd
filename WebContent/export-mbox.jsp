<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@ page import="edu.stanford.muse.AddressBookManager.AddressBook" %>
<%@include file="getArchive.jspf" %>
<!DOCTYPE HTML>
<html>
<head>
    <title>Export</title>

    <link rel="icon" type="image/png" href="images/epadd-favicon.png">

    <script src="js/jquery.js"></script>

    <link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
    <!-- Optional theme -->
    <script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>

    <jsp:include page="css/css.jsp"/>
    <script src="js/muse.js"></script>
    <script src="js/epadd.js"></script>
</head>
<body>
<%@include file="header.jspf"%>
<script>epadd.nav_mark_active('Export');</script>
<%
	writeProfileBlock(out, archive,  "Export archive");
%>
<div style="margin-left:170px">
    <div id="spinner-div" style="text-align:center; position:fixed; left:50%; top:50%"><img style="height:20px" src="images/spinner.gif"/></div>

<%
    // attachmentsForDocs

    String dir = Archive.TEMP_SUBDIR + File.separator;
    new File(dir).mkdir();//make sure it exists
    //create mbox file in localtmpdir and put it as a link on appURL.

//    String dir = request.getParameter ("dir");
    File f = new File(dir);

    String docsetID=request.getParameter("docsetID");
    String fname = Util.nullOrEmpty(docsetID) ? "epadd-export-all.mbox" : "epadd-export-" + docsetID + ".mbox";
    String fnameOnlyHeaders = Util.nullOrEmpty(docsetID) ? "epadd-export-all-only-headers.mbox" : "epadd-export-" + docsetID + "-only-headers.csv";

    String attachmentdirname = f.getAbsolutePath() + File.separator + (Util.nullOrEmpty(docsetID)? "epadd-all-attachments" : "epadd-all-attachments-"+docsetID);
    new File(attachmentdirname).mkdir();

    String pathToFile = f.getAbsolutePath() + File.separator + fname;
    String pathToFileOnlyHeaders = f.getAbsolutePath() + File.separator + fnameOnlyHeaders;

    PrintWriter pw = null;
    try {
        pw = new PrintWriter(pathToFile, "UTF-8");
    } catch (Exception e) {
        out.println ("Sorry, error opening mbox file: " + e + ". Please see the log file for more details.");
        Util.print_exception("Error opening mbox file: ", e, JSPHelper.log);
        return;
    }
    PrintWriter pwOnlyHeaders = null;
    try {
        pwOnlyHeaders = new PrintWriter(pathToFileOnlyHeaders, "UTF-8");
    } catch (Exception e) {
        out.println ("Sorry, error opening csv file: " + e + ". Please see the log file for more details.");
        Util.print_exception("Error opening csv file: ", e, JSPHelper.log);
        return;
    }
    //check if request contains docsetID then work only on those messages which are in docset
    //else export all messages of mbox.
    Collection<Document> selectedDocs;

    if(!Util.nullOrEmpty(docsetID)){
        DataSet docset = (DataSet) session.getAttribute(docsetID);
        selectedDocs = docset.getDocs();
    }else {
        selectedDocs = new LinkedHashSet<>(archive.getAllDocs());
    }
    JSPHelper.log.info ("export mbox has " + selectedDocs.size() + " docs");


// either we do tags (+ or -) from selectedTags
    // or we do all docs from allDocs
    BlobStore bs = null;
    bs = archive.getBlobStore();

    String noAttach = request.getParameter("noattach");
    boolean noAttachments = "on".equals(noAttach);
    boolean stripQuoted = "on".equals(request.getParameter("stripQuoted"));
    for (Document ed: selectedDocs)
        EmailUtils.printToMbox(archive, (EmailDocument) ed, pw, noAttachments ? null: bs, stripQuoted);
    pw.close();
    String appURL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
    String contentURL = "serveTemp.jsp?archiveID="+archiveID+"&file=" + fname ;
    String linkURL = appURL + "/" +  contentURL;

    //Only headers information for search result csv format
    EmailUtils.printCsvHeader(pwOnlyHeaders);
    for (Document ed: selectedDocs)
        EmailUtils.printToCsv(archive, (EmailDocument) ed, pwOnlyHeaders, null, stripQuoted);
    pwOnlyHeaders.close();
    String appURLOnlyHeaders = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
    String contentURLOnlyHeaders = "serveTemp.jsp?archiveID="+archiveID+"&file=" + fnameOnlyHeaders ;
    String linkURLOnlyHeaders = appURLOnlyHeaders + "/" + contentURLOnlyHeaders;





    /* Code to export attachments for the given set of documents as zip file*/
    Map<Blob, String> blobToErrorMessage = new LinkedHashMap<>();
    //Copy all attachment to a temporary directory and then zip it and allow transfer to the client
    int nBlobsExported = Archive.getnBlobsExported(selectedDocs, archive.getBlobStore(),null, attachmentdirname, false, null, false, blobToErrorMessage);
    //now zip the attachmentdir and create a zip file in TMP
    String zipfile = Archive.TEMP_SUBDIR+ File.separator + "all-attachments.zip";
    Util.deleteAllFilesWithSuffix(Archive.TEMP_SUBDIR,"zip",JSPHelper.log);
    Util.zipDirectory(attachmentdirname, zipfile);
    //return it's URL to download
    String attachmentURL = "serveTemp.jsp?archiveID="+archiveID+"&file=all-attachments.zip" ;
    String attachmentDownloadURL = appURL + "/" +  attachmentURL;

%>

    <br/>

    <a href =<%=linkURL%>>Download mbox file</a>
    <p></p>
    This file is in mbox format, and can be accessed with many email clients (e.g. <a href="http://www.mozillamessaging.com/">Thunderbird</a>.)
    It can also be viewed with a text editor.<br/>
    On Mac OS X, Linux, and other flavors of Unix, you can usually open a terminal window and type the command: <br/>
    <i>mail -f &lt;saved file&gt;</i>.
    <p>
        This mbox file may also have extra headers like X-ePADD-Folder, X-ePADD-Labels and X-ePADD-Annotation.
    </p>


    <a href =<%=linkURLOnlyHeaders%>>Download search result in csv format</a>
    <p></p>
    Download search result in csv format
    </p>

    <br/>
    <a href =<%=attachmentDownloadURL%>>Download attachments in a zip file</a>
    <p></p>
    This file is in zip format, and contains all attachments in the selected messages.<br/>
    <br/>

</div>
<jsp:include page="footer.jsp"/>
</body>
</html>
