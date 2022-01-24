<%@page language="java" contentType="application/json;charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="org.json.*"%>

<%@ page import="org.json.JSONObject"%>
<%@ page import="edu.stanford.muse.AddressBookManager.AddressBook"%>
<%@ page import="edu.stanford.muse.AddressBookManager.Contact"%>
<%@page import="edu.stanford.muse.index.Archive"%>
<%@ page import="edu.stanford.muse.index.EmailDocument"%>
<%@ page import="edu.stanford.muse.index.ArchiveReaderWriter"%>
<%@ page import="edu.stanford.muse.index.SearchResult"%>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.util.EmailUtils"%>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.webapp.HTMLUtils" %>
<%@ page import="java.util.Arrays"%>
<%@ page import="java.util.HashSet"%>
<%@ page import="java.util.Iterator"%>
<%@ page import="java.util.LinkedHashSet"%>
<%@ page import="java.util.Set"%>

<% 
    JSONObject result = new JSONObject();
    String error = null;
    boolean status = true;

    // Merge two or more correspondents and save them in address book
    String archiveID = request.getParameter("archiveID");
    String mergeIDs = request.getParameter("mergeIDs");
    //split mergeIDs on colon

    String[] mergeIDs_split = mergeIDs.split(",");
    Set<String> mergeContactID=new LinkedHashSet<>();
    Arrays.stream(mergeIDs_split).forEach(s->mergeContactID.add(s));

    if ("null".equals(archiveID)) {
        error = "Sorry, no archive ID found";
    } else if (mergeContactID.size() < 2 ) {
        error = "Sorry, two or more contact is required for merging";
    } else {
	    Archive archive = JSPHelper.getArchive(request);
        AddressBook ab = archive.getAddressBook();
        Contact newone = new Contact();
        Set<Contact> mergedContacts = new HashSet<Contact>();

         for (Iterator<String> it = mergeContactID.iterator(); it.hasNext(); ) {
            String contactID = it.next();
            int c_id = -1;
            try {
                c_id = Integer.parseInt(contactID);
            } catch (NumberFormatException e) {
                error = "invalid contact ID: " + c_id;
                break;
            }

            if (c_id >= 0) {
                Contact c = ab.getContact(c_id);

                if ( c == null) {
                    error = "No such Contact with c_id: " + c_id;
                    break;
                } else {
                    mergedContacts.add(c);
                }
            } else {
                error = "invalid contact ID: "+ c_id;
                break;
            }
        }

        // If there is no error received... merge contacts
        if (error == null) {
           //merge all contacts into a new combined one.
            mergedContacts.forEach(contact->{
                newone.merge(contact);
            });

		    //add contact to contactListForIds
			ab.contactListForIds.add(newone);

            // remove merged contacts
            ab.removeContacts(mergedContacts);

            //fill summary objects.
            ab.fillL1_SummaryObject(archive.getAllDocs());

            EmailDocument.recomputeAddressBook(archive,new LinkedHashSet<>()); //because owner has changed and recomputation assumes owner as a trusted address. So we need to recompute it.

            try {
                archive.recreateCorrespondentAuthorityMapper(); // we have to recreate auth mappings since they may have changed
                ArchiveReaderWriter.saveAddressBook(archive, Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);
                ArchiveReaderWriter.saveCorrespondentAuthorityMapper(archive, Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    if (error != null) {
       result.put("status", 1);
       result.put ("error", error);
    } else {
        result.put ("status", 0);
    }
    out.println (result.toString(4));
%>
