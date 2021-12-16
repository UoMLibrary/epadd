<%@page language="java" contentType="application/json;charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="org.json.*"%>

<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@include file="../getArchive.jspf" %>

<%
    //session.setAttribute("statusProvider", new StaticStatusProvider("Uploading files"));

    // 1. referencing to addressbook, a new copy of addressbook would be duplicated by copying line by line
    // 2. when reaching the line(s) for the modified correspondent entry, i.e. newdata param, the content would be digested into address book
    // 3. if everything goes well, the clone copy would overwrite as act as the addressbook master

    String archiveID = request.getParameter("archiveID");
    String row = request.getParameter("row");
    String newdata = request.getParameter("data");   // newdata is to new piece of correspondent information to be digested into addressbook

    JSONObject result = new JSONObject();
    String error = "";
    int status = 0;

    if(row== null){
	    result.put("status", 1);
        result.put("error", "row param is null");
        out.println(result.toString());
	    return;
	}

    int rowindex = 0;
    try {
        rowindex = Integer.parseInt(row);

    } catch (Exception e) {
        result.put("status", 1);
        result.put("error", "row param is not a digit");
        out.println(result.toString());
	    return;
    }

	if(newdata == null){
	    result.put("status", 1);
        result.put("error", "newdata param is null");
        out.println(result.toString());
	    return;
	}
    newdata = newdata.trim();

    //addressbook to be cloned and to be overwritten if everything goes well
    String addressbookpath = archive.baseDir + File.separator + Archive.BAG_DATA_FOLDER + File.separator + Archive.SESSIONS_SUBDIR + File.separator + Archive.ADDRESSBOOK_SUFFIX;

    String destdir = Archive.TEMP_SUBDIR + File.separator;
	if(!new File(destdir).exists())//if no directory exists then create it.
       new File(destdir).mkdir();

    //cloned addressbook transiently created in temp dir for manipulating purpose
    String updateaddressbookpath = destdir+File.separator+Archive.ADDRESSBOOK_SUFFIX+".txt";

	if(!new File(addressbookpath).exists()){
	    result.put("status", 1);
        result.put("error", "address book does not exist");
        out.println(result.toString());
	    return;
	}

    // Prepare input output streams
    BufferedReader addressbook = null;
    BufferedWriter addressbookDuplicate = null;
    boolean skip = false;
    boolean hit = false;       // if target correspondent is found in existing addressbook
    boolean owner = false;      // if current correspondent is marked owner in existing addressbook
    int counter = -1;            // counter to indicate current processing correspondent

    try {
    	// Open streams
    	addressbook = new BufferedReader(new FileReader(addressbookpath));                  // input
    	addressbookDuplicate = new BufferedWriter(new FileWriter(updateaddressbookpath));   // output

        int numOfMessageWritten = 0;
        final int N_MAX_MESSAGE_TO_FLUSHED = 2*1024;    // experimental

    	// Write file contents to response.
    	String olddata;

        //referencing to addressbook, a new copy of addressbook would be duplicated by copying line by line
    	while ((olddata = addressbook.readLine()) != null ) {

            // if we have reach the line(s) for modified entry, we ignore all data lines but just write back end delimiter "--"
    	    if (skip){

    	        if (olddata.trim().equals("--")) {

                    if (newdata!=null && !newdata.equals("")) {
                        addressbookDuplicate.append("--");
                        addressbookDuplicate.newLine();
                    }
                    skip = false;
                }
                //we ignore all data lines here
                continue;
       	    }

    		if (!hit && counter == rowindex ) {
    		    // when reaching the line(s) for the modified correspondent entry, i.e. newdata param, the content could be revised according to newdata param
    		    // do it only once time
    		    skip = true;
    		    hit = true;

                if (newdata!=null && !newdata.equals("")) {
                    addressbookDuplicate.append(newdata);
                    addressbookDuplicate.newLine();

                    numOfMessageWritten ++;
                    counter++;
                }

                if (owner && newdata.equals("")){
                    status = 1;
                    error = "Error updating addressbook- archive owner cannot be erased";
                    JSPHelper.log.info ("Error updating addressbook- archive owner cannot be erased");

                    throw new Exception(error);
                }

    		} else {
    		    // otherwise, referencing to addressbook and a new copy of addressbook would be duplicated by copying line by line
    		    addressbookDuplicate.append(olddata);
    		    addressbookDuplicate.newLine();

    		    numOfMessageWritten ++;

                // check if delimiter is encountered and check if it is archive owner
                if (olddata.trim().equals("-- Archive owner") ) {
                        owner = true;
                        counter++;
                }
                if (olddata.trim().equals("--")) {
                        owner = false;
                        counter++;
                }
    		}

            if (numOfMessageWritten == N_MAX_MESSAGE_TO_FLUSHED) { // experimental
                  numOfMessageWritten = 0;
                  addressbookDuplicate.flush();
            }
    	}
    } catch(Exception e) {
        status = 1;
        error = e.toString();
        JSPHelper.log.info ("Error duplicating addressbook content to archive tmp");

    } finally {
    	// Gently close streams.
    	Util.close(addressbookDuplicate);
    	Util.close(addressbook);
    }

    if (hit && status == 0) {
        // if everything goes well, the clone copy would overwrite as act as the addressbook master
        ArchiveReaderWriter.updateAddressBook(archive, updateaddressbookpath);
    }

    result.put("status", status);
    result.put("error", error);
    out.println(result.toString());

    //session.removeAttribute("statusProvider");
%>