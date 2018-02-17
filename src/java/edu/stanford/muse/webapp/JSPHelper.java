/*
 * Copyright (C) 2012 The Stanford MobiSocial Laboratory
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.stanford.muse.webapp;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.email.*;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.exceptions.NoDefaultFolderException;
import edu.stanford.muse.index.*;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.model.DummyNERModel;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.ner.model.SequenceModel;
import edu.stanford.muse.util.*;
import edu.stanford.muse.util.SloppyDates.DateRangeSpec;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.TransformerException;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/* import javax.servlet.jsp.JspWriter; */

/**

 * helper code that would otherwise clutter JSPs. its ok for this file to handle

 * HTML/HTTP.
 * All HTML/HTTP and presentation Java code must be present only in this file,
 * because the rest of the Muse code can be used in other, non-web apps.
 */
public class JSPHelper {
	public static Log		log						= LogFactory.getLog(JSPHelper.class);

	public static final int	nColorClasses			= 20;
	public final static int	DEFAULT_N_CARD_TERMS	= 30;

	// jetty processes request params differently from tomcat
	// tomcat assumes incoming params (both get/post) are iso8859.
	// jetty assumes utf-8.
	// so if we are not running jetty, we convert the string from iso8859 to utf8, which is what we eventually want.
	// can set RUNNING_ON_JETTY in either of 2 ways:
	// define -Dmuse.container=jetty
	// check request.getSession().getServletContext().getServerInfo()

	public static boolean	RUNNING_ON_JETTY		= false;

	static {
		// log4j initialize should be called before anything else happens. this is the best place to initialize
		Log4JUtils.initialize();
		// check container
		String container = System.getProperty("muse.container");
		if ("jetty".equals(container))
		{
			RUNNING_ON_JETTY = true;
			log.info("Running on the Jetty web server (due to system property muse.container: " + RUNNING_ON_JETTY + ")");
		}
	}

	public static void checkContainer(HttpServletRequest req)
	{
		String serverInfo = req.getSession().getServletContext().getServerInfo();
		log.info("Running inside container: " + serverInfo);
		if (serverInfo.toLowerCase().indexOf("jetty") >= 0)
			RUNNING_ON_JETTY = true;
		log.info("Running on jetty: " + RUNNING_ON_JETTY);
	}

	/**
	 * gets the LOCAL/CLIENT session attribute (to make explicit distinction
	 * from shared/global/server session)
	 */
	private static Object getHttpSessionAttribute(HttpSession session, String attr_name)
	{
		return session.getAttribute(attr_name); // intentional use of session . getSessionAttribute()
	}

	public static String getUserKey(HttpSession session)
	{
		String userKey = (String) getHttpSessionAttribute(session, "userKey");
		// do sanitizeFileName for security reason also
		return Util.sanitizeFileName(userKey);
	}

	/** gets the cache (base) dir for the current session */
	private static String getBaseDir(MuseEmailFetcher m, HttpServletRequest request)
	{
		HttpSession session = request.getSession();

		if (session == null)
			return SimpleSessions.getDefaultCacheDir();
		else
		{
			String baseDir = (String) getHttpSessionAttribute(session, "cacheDir");
			// if we have an existing cache dir, use it
			if (!Util.nullOrEmpty(baseDir))
				return baseDir;

			String userKey = "user";

			if (ModeConfig.isMultiUser()) // (!runningOnLocalhost(request))
			{
				Collection<EmailStore> emailStores = m.emailStores;
				if (Util.nullOrEmpty(emailStores))
					return "";

				// go through the stores till we have a non-empty key
				for (EmailStore store : emailStores)
				{
					String e = store.emailAddress;
					if (!Util.nullOrEmpty(e))
					{
						userKey = e;
						break;
					}
				}
			}

			session.setAttribute("userKey", userKey); // this is needed for the root dir for piclens
			// now we have the user key				
			baseDir = SimpleSessions.getDefaultRootDir() + File.separator + getUserKey(session);
			log.info("base dir set to " + baseDir);
			return baseDir;
		}
	}

	public static Archive getArchive(HttpServletRequest request)
	{
		//get archiveID from request parameter..
		String archiveID = request.getParameter("archiveID");
		if(archiveID==null){
			//if only single archive is present in the map then return that
			//else return null after logging this information.
//			Archive onlyleft = SimpleSessions.getDefaultArchiveFromGlobalArchiveMap();
//			if(onlyleft==null){
				log.debug("No archive ID parameter passed from the client");
			//present for the given archive ID. No default archive could be loaded");
				return null;
//			}else{
//				log.debug("No archiveID parameter passed from the client. Loading the default archive.");
//				return onlyleft;
//			}
		}
		return SimpleSessions.getArchiveForArchiveID(archiveID);
	}

	/**
	 * gets the session attribute - look up from the client-side session storage
	 * first then the server-side.
	 * session name (archive name) is determined by attribute ARCHIVE_NAME_STR`
	 * of the client-side session.
	 */
	public static Object getSessionAttribute(HttpSession session, String attr_name)
	{
		Object value = getHttpSessionAttribute(session, attr_name);
		return value;
		//		if (value != null) return value;

		//		now should not need to special case for !ModeConfig.isPublicMode().
		//		if required, should perhaps special case for !"archive".equals(attr_name) instead.
		//		// not loading global sessions unless public mode
		//		if (!ModeConfig.isPublicMode())
		//			return null;

		//		Map<String, Object> s = null;
		//		try {
		//			s = Sessions.getGlobalSession(session);
		//		} catch (Exception e) { Util.print_exception(e, log); }
		//		
		//		if (s != null) return s.get(attr_name);
		//
		//		return null;
	}

	/**
	 * perform XSLT transformation on the XML
	 * 
	 * @param xml_fname
	 * @param xsl_fname
	 * @param out
	 * @throws TransformerException
	 */
	/*
	 * public static void xsltTransform(String xml_fname, String xsl_fname, JspWriter out) throws TransformerException
	 * {
	 * TransformerFactory factory = TransformerFactory.newInstance();
	 * Source xslt = new StreamSource(new File(xsl_fname));
	 * Transformer transformer = factory.newTransformer(xslt);
	 * 
	 * Source text = new StreamSource(new File(xml_fname));
	 * transformer.transform(text, new StreamResult(out));
	 * }
	 */

	/**
	 * gets the root dir for the logged in user -- this is the dir.
	 * corresponding to /<userkey>
	 * inside the actual webapp dir.
	 * currently used only for attachments and save messages. the eventual goal
	 * is to get
	 * rid of this method because it is not secure in a multi-user environment.
	 */
	public static String getRootDir(HttpServletRequest request)
	{
		HttpSession session = request.getSession();
		String userKey = (String) getSessionAttribute(session, "userKey");
		ServletContext application = session.getServletContext();
		String documentRootPath = application.getRealPath("/").toString();

		return documentRootPath + File.separatorChar + userKey;
	}

	/**
	 * converts an array of strings from iso-8859-1 to utf8. useful for
	 * converting i18n chars in http request parameters.
	 * if throwExceptionIfUnsupportedEncoding is true, throws an exception,
	 * otherwise returns
	 */
	private static String[] convertRequestParamsToUTF8(String params[], boolean throwExceptionIfUnsupportedEncoding) throws UnsupportedEncodingException
	{
		if (RUNNING_ON_JETTY)
			return params;
		if (params == null)
			return null;

		// newParams will contain only the strings that successfully can be converted to utf-8
		// others will be reported and ignored
		List<String> newParams = new ArrayList<String>();
		for (int i = 0; i < params.length; i++)
		{
			try {
				newParams.add(convertRequestParamToUTF8(params[i]));
			} catch (UnsupportedEncodingException e) {
				log.warn("Unsupported encoding exception for " + params[i]);
				Util.print_exception(e, log);
				if (throwExceptionIfUnsupportedEncoding)
					throw (e);
				// else swallow it
			}
		}
		return newParams.toArray(new String[newParams.size()]);
	}

	public static String[] convertRequestParamsToUTF8(String params[]) throws UnsupportedEncodingException
	{
		return convertRequestParamsToUTF8(params, true);
	}

	// key finding after a lot of experimentation with jetty and tomcat.
	// make all pages UTF-8 encoded.
	// setRequestEncoding("UTF-8") before reading any parameter
	// even with this, with tomcat, GET requests are iso-8859-1.
	// so convert in that case only...
	// converts an array of strings from iso-8859-1 to utf8. useful for converting i18n chars in http request parameters
	public static String convertRequestParamToUTF8(String param) throws UnsupportedEncodingException
	{
		if (RUNNING_ON_JETTY)
		{
			log.info("running on jetty: no conversion for " + param);
			return param;
		}
		if (param == null)
			return null;
		String newParam = new String(param.getBytes("ISO-8859-1"), "UTF-8");
		if (!newParam.equals(param))
			log.info("Converted to utf-8: " + param + " -> " + newParam);
		return newParam;
	}

	public static boolean runningOnLocalhost(HttpServletRequest request)
	{
		//return "localhost".equals(request.getServerName());
		return !ModeConfig.isMultiUser();
	}

    public static boolean runningOnMuseMachine(HttpServletRequest request){
        String sn = request.getServerName();
        if(sn!=null){
            return sn.contains("stanford");
        }
        return false;
    }

	public static boolean runningOnAshokaMachine(HttpServletRequest request){
		String sn = request.getServerName();
		if(sn!=null){
			return sn.contains("ashoka.edu.in");
		}
		return false;
	}

	//	/* this version of fetchemails must have folders defined in request since there is no primary email address */
	//	public static Triple<Collection<EmailDocument>, AddressBook, BlobStore> fetchEmails(HttpServletRequest request, HttpSession session, boolean download) throws Exception
	//	{
	//		return fetchEmails (request, session, download, /* downloadattachments = */ false, false);
	//	}
	//
	//	/** fetches messages without downloading or attachments.
	//	 * support default folder for primary email address */
	//	public static Triple<Collection<EmailDocument>, AddressBook, BlobStore> fetchEmails(HttpServletRequest request, HttpSession session, String primaryEmailAddress) throws Exception
	//	{
	//		return fetchEmails (request, session, false, false, false);
	//	}
	//
	//	public static boolean fetchEmailsDefaultFolders(HttpServletRequest request, HttpSession session, boolean downloadMessageText, boolean downloadAttachments) throws Exception
	//	{
	//		try { 
	//			fetchEmails(request, session, downloadMessageText, downloadAttachments, true);
	//		} catch (Exception e) {
	//			return false;
	//		}
	//		return true;
	//	}
	//	
	//	public static Triple<Collection<EmailDocument>, AddressBook, BlobStore> fetchEmails(HttpServletRequest request, HttpSession session, boolean downloadMessageText, boolean downloadAttachments, boolean useDefaultFolders) 
	//			throws UnsupportedEncodingException, MessagingException, InterruptedException, IOException, JSONException, NoDefaultFolderException, CancelledException
	//	{
	//		return fetchEmails(request, session, downloadMessageText, downloadAttachments, useDefaultFolders, null);
	//	}

	/**
	 * A VIP method.
	 * reads email accounts and installs addressBook and emailDocs into session
	 * useDefaultFolders: use the default folder for that fetcher if there are
	 * no explicit folders in that fetcher.
	 * throws out of memory error if it runs out of memory.
	 * 
	 * @throws JSONException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws MessagingException
	 * @throws UnsupportedEncodingException
	 * @throws NoDefaultFolderException
	 * @throws Exception
	 */
	public static void fetchAndIndexEmails(Archive archive, MuseEmailFetcher m, HttpServletRequest request, HttpSession session, boolean downloadMessageText, boolean downloadAttachments, boolean useDefaultFolders)
			throws MessagingException, InterruptedException, IOException, JSONException, NoDefaultFolderException, CancelledException, OutOfMemoryError {
        // first thing, set up a static status so user doesn't see a stale status message
        session.setAttribute("statusProvider", new StaticStatusProvider("Starting up..."));

        checkContainer(request);
        String encoding = request.getCharacterEncoding();
        log.info("request parameter encoding is " + encoding);

        if (!downloadMessageText)
            if ("true".equalsIgnoreCase(request.getParameter("downloadMessageText"))) {
                downloadMessageText = true;
                log.info("Downloading message text because advanced option was set");
            }

        if (!downloadAttachments)
            if ("true".equalsIgnoreCase(request.getParameter("downloadAttachments"))) {
                downloadAttachments = true;
                downloadMessageText = true; // because text is needed for attachment wall -- otherwise we can't break out from piclens to browsing messages associated with a particular thumbnail
                log.info("Downloading attachments because advanced option was set");
            }

        String[] allFolders = request.getParameterValues("folder");
        if (allFolders != null) {
            // try to read folder strings, first checking for exceptions
            try {
                allFolders = JSPHelper.convertRequestParamsToUTF8(allFolders, true);
            } catch (UnsupportedEncodingException e) {
                // report exception and try to read whatever folders we can, ignoring the exception this time
                log.warn("Unsupported encoding exception: " + e);
                try {
                    allFolders = JSPHelper.convertRequestParamsToUTF8(allFolders, false);
                } catch (UnsupportedEncodingException e1) {
                    log.warn("Should not reach here!" + e1);
                }
            }
        }

        Multimap<String, String> requestMap = convertRequestToMap(request);
        Filter filter = Filter.parseFilter(requestMap);
        // if required, forceEncoding can go into fetch config
        //	String s = (String) session.getAttribute("forceEncoding");
        FetchConfig fc = new FetchConfig();
        fc.downloadMessages = downloadMessageText;
        fc.downloadAttachments = downloadAttachments;
        fc.filter = filter;

        archive.setBaseDir(getBaseDir(m, request));
        m.fetchAndIndexEmails(archive, allFolders, useDefaultFolders, fc, session);
        //make sure the archive is dumped at this point
        archive.close();
        archive.openForRead();

        //perform entity IE related tasks only if the message text is available
        if (downloadMessageText) {
            String modelFile = SequenceModel.MODEL_FILENAME;
            NERModel nerModel=null;
            //=(SequenceModel) session.getAttribute("ner");
            session.setAttribute("statusProvider", new StaticStatusProvider("Loading NER sequence model from resource: " + modelFile + "..."));
            try {
                if (System.getProperty("muse.dummy.ner") != null) {
                    log.info("Using dummy NER model, all CIC patterns will be treated as valid entities");
                    nerModel = new DummyNERModel();
                } else {
                    log.info("Loading NER sequence model from: " + modelFile + " ...");
                    nerModel = SequenceModel.loadModelFromRules(SequenceModel.RULES_DIRNAME);
                }
            } catch (IOException e) {
                Util.print_exception("Could not load the sequence model from: " + modelFile, e, log);
            }
            if (nerModel == null) {
                log.error("Could not load NER model from: " + modelFile);
            } else {
                NER ner = new NER(archive, nerModel);
                session.setAttribute("statusProvider", ner);
                ner.recognizeArchive();
                //Here, instead of getting the count of all entities (present in ner.stats object)
				//get the count of only those entities which pass a given thersold.
				//This is to fix a bug where the count of person entities displayed on browse-top.jsp
				//page was different than the count of entities actually displayed following a thersold.
				// @TODO make it more modular
                //archive.processingMetadata.entityCounts = ner.stats.counts;
				double theta = 0.001;
				archive.processingMetadata.entityCounts = Archive.getEntitiesCountMapModuloThersold(archive,theta);
                log.info(ner.stats);
            }
           // archive.processingMetadata.numPotentiallySensitiveMessages = archive.numMatchesPresetQueries();
            log.info("Number of potentially sensitive messages " + archive.processingMetadata.numPotentiallySensitiveMessages);

            //Is there a reliable and more proper way of checking the mode it is running in?
            String logF = System.getProperty("muse.log");
            if (logF == null || logF.endsWith("epadd.log")) {
                try {
//                InternalAuthorityAssigner assignauthorities = new InternalAuthorityAssigner();
//                session.setAttribute("statusProvider", assignauthorities);
//                assignauthorities.initialize(archive);
//                if (!assignauthorities.isCancelled())
//                    request.getSession().setAttribute("authorities", assignauthorities);
//                else
//                    assignauthorities = null;
//                boolean success = assignauthorities.checkFeaturesIndex(archive, true);
//                if (!success) {
//                    log.warn("Could not build context mixtures for entities");
//                } else
//                    log.info("Successfully built context mixtures for entities");
                } catch (Exception e) {
                    log.warn("Exception while building context mixtures", e);
                }
            }
        }
        // add the new stores
    }

	/*
	 * creates a new blob store object from the given location (may already
	 * exist) and returns it
	 */
	private static BlobStore preparedBlobStore(String baseDir)
	{
		// always set up attachmentsStore even if we are not fetching attachments
		// because the user may already have stuff in it -- if so, we should make it available.
		String attachmentsStoreDir = baseDir + File.separator + Archive.BLOBS_SUBDIR + File.separator;
		BlobStore attachmentsStore = null;
		try {
			new File(attachmentsStoreDir).mkdirs();
			attachmentsStore = new BlobStore(attachmentsStoreDir);
		} catch (IOException ioe) {
			log.error("MAJOR ERROR: Disabling attachments because unable to initialize attachments store in directory: " + attachmentsStoreDir + " :" + ioe + " " + Util.stackTrace(ioe));
			attachmentsStore = null;
		}
		return attachmentsStore;
	}

	public static Archive preparedArchive(HttpServletRequest request, String baseDir) throws IOException
	{
		return preparedArchive(request, baseDir, null);
	}

	/** creates a new archive and returns it */
	public static Archive preparedArchive(HttpServletRequest request, String baseDir, List<String> extraOptions) throws IOException
	{
		List<String> list = new ArrayList<String>();

		if (request != null)
		{
			if ("yearly".equalsIgnoreCase(request.getParameter("period")))
				list.add("-yearly");

			if (request.getParameter("noattachments") != null)
				list.add("-noattachments");

			// filter params
			if ("true".equalsIgnoreCase(request.getParameter("sentOnly")))
				list.add("-sentOnly");

			String str = request.getParameter("dateRange");
			if (str != null && str.length() > 0)
			{
				list.add("-date");
				list.add(str);
			}
			String keywords = request.getParameter("keywords");
			if (keywords != null && !keywords.equals(""))
			{
				list.add("-keywords");
				list.add(keywords);
			}

			String filter = request.getParameter("filter");
			if (filter != null && !filter.equals(""))
			{
				list.add("-filter");
				list.add(filter);
			}
			// end filter params

			// advanced options
			if ("true".equalsIgnoreCase(request.getParameter("incrementalTFIDF")))
				list.add("-incrementalTFIDF");
			if ("true".equalsIgnoreCase(request.getParameter("NER")))
				list.add("-NER");
			if (!"true".equalsIgnoreCase(request.getParameter("allText")))
				list.add("-noalltext");
			if ("true".equalsIgnoreCase(request.getParameter("locationsOnly")))
				list.add("-locationsOnly");
			if ("true".equalsIgnoreCase(request.getParameter("orgsOnly")))
				list.add("-orgsOnly");
			if ("true".equalsIgnoreCase(request.getParameter("includeQuotedMessages")))
				list.add("-includeQuotedMessages");
			String subjWeight = request.getParameter("subjectWeight");
			if (subjWeight != null)
			{
				list.add("-subjectWeight");
				list.add(subjWeight);
			}
		}

		if (!Util.nullOrEmpty(extraOptions))
			list.addAll(extraOptions);

		String[] s = new String[list.size()];
		list.toArray(s);

		// careful about the ordering here.. first setup, then read indexer, then run it
		Archive archive = Archive.createArchive();
        BlobStore blobStore = JSPHelper.preparedBlobStore(baseDir);
        archive.setup(baseDir, blobStore, s);
		log.info("archive setup in " + baseDir);
		return archive;
	}

	public static String getRequestDescription(HttpServletRequest request)
	{
		return getRequestDescription(request, true);
	}

	/** also sets current thread name to the path of the request */
	private static String getRequestDescription(HttpServletRequest request, boolean includingParams)
	{
		HttpSession session = request.getSession();
		String page = request.getServletPath();
		Thread.currentThread().setName(page);
		String userKey = (String) session.getAttribute("userKey");
		StringBuilder sb = new StringBuilder("Request[" + userKey + "@" + request.getRemoteAddr().toString() + "]: " + request.getRequestURL());
		// return here if params are not to be included
		if (!includingParams)
			return sb.toString();

		String link = request.getRequestURL() + "?";

		Map<String, String[]> rpMap = request.getParameterMap();
		if (rpMap.size() > 0)
			sb.append(" params: ");
		for (Object o : rpMap.keySet())
		{
			String str1 = (String) o;
			sb.append(str1 + " -> ");
			if (str1.startsWith("password"))
			{
				sb.append("*** ");
				continue;
			}

			String[] vals = rpMap.get(str1);
			if (vals.length == 1)
				sb.append(Util.ellipsize(vals[0], 100));
			else
			{
				sb.append("{");
				for (String x : vals)
					sb.append(Util.ellipsize(x, 100) + ",");
				sb.append("}");
			}
			sb.append(" ");

			for (String val : vals)
				link += (str1 + "=" + vals[0] + "&");
		}

		sb.append(" link: " + link);
		return sb.toString();
	}

	public static void logRequest(HttpServletRequest request, boolean includingParams)
	{
		log.info("NEW " + getRequestDescription(request, includingParams));
	}

	public static void logRequest(HttpServletRequest request)
	{
		log.info("NEW " + getRequestDescription(request, true));
	}

	public static void logRequestComplete(HttpServletRequest request)
	{
		log.info("COMPLETED " + getRequestDescription(request, true));
		String page = request.getServletPath();
		Thread.currentThread().setName("done-" + page);
	}

	public static Multimap<String, String> convertRequestToMap(HttpServletRequest request) throws UnsupportedEncodingException {
		Multimap<String, String> params = LinkedHashMultimap.create();
		{
			if (true) {
				// regular file encoding
				Enumeration<String> paramNames = request.getParameterNames();

				while (paramNames.hasMoreElements()) {
					String param = paramNames.nextElement();
					String[] vals = request.getParameterValues(param);
					if (vals != null)
						for (String val : vals)
							params.put(param, JSPHelper.convertRequestParamToUTF8(val));
				}
			}
		}

		return params;
	}

	/** returns a single value for the given key */
	public static String getParam(Multimap<String, String> params, String key) {
		Collection<String> values = params.get(key);
		if (values.size() == 0)
			return null;
		return values.iterator().next();
	}


	/** returns multiple value for the given key */
	public static Collection<String> getParams(Multimap<String, String> params, String key) {
		Collection<String> values = params.get(key);
		return values;
	}

	// returns just the request params as a string
	public static String getRequestParamsAsString(HttpServletRequest request)
	{
		// could probably also do it by looking at request url etc.
		String requestParams = "";
		Map<String, String[]> rpmap = request.getParameterMap();
		for (String key : rpmap.keySet())
			for (String value : rpmap.get(key))
				// rpmap has a string array for each param
				requestParams += (key + '=' + value + "&");
		return requestParams;
	}

	/** invoke only from getHTMLForHeader, needs specific context of date etc. */
	public static StringBuilder getHTMLForDate(Date date)
	{
		StringBuilder result = new StringBuilder();

		if (date != null)
		{
			Calendar c = new GregorianCalendar();
			c.setTime(date);
			int year = c.get(Calendar.YEAR);
			int month = c.get(Calendar.MONTH) + 1; // we need 1-based, not 0-based.
			String yearOnclick = "\"javascript:window.location='browse?year=" + year + "'\"";
			String monthOnclick = "\"javascript:window.location='browse?year=" + year + "&month=" + month + "'\"";
			String monthSpan = "<span class=\"facet\" onclick=" + monthOnclick + ">" + CalendarUtil.getDisplayMonth(c) + "</span> ";
			String yearSpan = "<span class=\"facet\" onclick=" + yearOnclick + ">" + c.get(Calendar.YEAR) + "</span> ";
			result.append("<tr><td width=\"10%\" align=\"right\" class=\"muted\">Date: </td><td>" + monthSpan + " " + c.get(Calendar.DATE) + ", " + yearSpan);
			int hh = c.get(Calendar.HOUR_OF_DAY);
			int mm = c.get(Calendar.MINUTE);
			boolean pm = false;
			if (hh == 0)
				hh = 12;
			else if (hh > 12)
			{
				hh -= 12;
				pm = true;
			}
			result.append(" " + String.format("%d:%02d", hh, mm) + (pm ? "pm" : "am"));
			result.append("\n</td></tr>\n");
		}
		return result;
	}

	public static JSONArray formatAddressesAsJSON(Address addrs[]) throws JSONException
	{
		JSONArray result = new JSONArray();
		if (addrs == null)
			return result;
		int index = 0;
		for (int i = 0; i < addrs.length; i++)
		{
			Address a = addrs[i];
			if (a instanceof InternetAddress)
			{
				InternetAddress ia = (InternetAddress) a;
				JSONObject o = new JSONObject();
				o.put("email", Util.maskEmailDomain(ia.getAddress()));
				o.put("name", ia.getPersonal());
				result.put(index++, o);
			}
		}
		return result;
	}

	public static Pair<String, String> getNameAndURL(InternetAddress a, AddressBook addressBook)
	{
		String s = a.getAddress();
		if (s == null)
			s = a.getPersonal();
		if (s == null)
			return new Pair<String, String>("", "");

		// TODO (maybe after archive data structures re-org): below should pass archive ID to browse page
		if (addressBook == null) {
			return new Pair<String, String>(s, "browse?person=" + s);
		} else {
			Contact contact = addressBook.lookupByEmail(a.getAddress());
			return new Pair<String, String>(s, "browse?contact=" + addressBook.getContactId(contact));
		}
	}

	public static String getURLForGroupMessages(int groupIdx)
	{
		return "browse?groupIdx=" + groupIdx;
	}

	public static String docControls(String messagesLink, String attachmentsLink, String linksLink)
	{
		String result = "";
		if (messagesLink != null)
			result += "<a target=\"#\" href=\"" + messagesLink + "\"><img title=\"Messages\" src=\"/muse/images/email.jpg\" width=\"24\"/></a>\n";
		if (!ModeConfig.isPublicMode()) {
			if (attachmentsLink != null)
				result += "<a target=\"#\"  href=\"" + attachmentsLink + "\"><img title=\"Attachments\" width=\"24\" src=\"/muse/images/paperclip.png\"/></a>\n";
			if (linksLink != null)
				result += "<a target=\"_links\" href=\"" + linksLink + "\"><img title=\"Links\" width=\"24\" src=\"/muse/images/link.png\"/></a>\n";
		}
		return result;
	}

	/** only used by slant */
	public static List<LinkInfo> extractLinks(Archive archive, HttpSession session, Collection<Document> docsToIndex, AddressBook addressBook)
	{
		try {
			archive.setAddressBook(addressBook);
			session.setAttribute("statusProvider", new StaticStatusProvider("Extracting links"));
			archive.extractLinks(docsToIndex);
			return EmailUtils.getLinksForDocs(docsToIndex);
		} catch (Exception e)
		{
			Util.print_exception(e, log);
			session.setAttribute("errorMessage", "An exception occurred");
			session.setAttribute("exception", e);
			return null;
		}
	}

	// must be kept in sync with NewFilter.isRegexSearch()
	private static boolean isRegexSearch(HttpServletRequest request)
	{
		return "on".equals(request.getParameter("unindexed"));
	}





	private static Set<Document> getAllDocsAsSet(HttpSession session, boolean only_apply_to_filtered_docs, HttpServletRequest request)
	{
		Archive archive = JSPHelper.getArchive(request);

		if (!only_apply_to_filtered_docs)
			return archive.getAllDocsAsSet();

		Collection<Document> docs = (Collection<Document>) getSessionAttribute(session, "emailDocs");
		if (docs == null)
			return new LinkedHashSet<Document>(archive.getAllDocsAsSet());
		return new LinkedHashSet<Document>(docs);
	}

	/**
	 * returns whether filter is set. Note that a filter may be set, but in a
	 * way that covers all docs,
	 * in which case, we treat the filter as effectively not set.
	 */
	public static boolean isFilterSet(HttpSession session)
	{
		// if filter set, emailDocs is set and the # of docs it has is < archive docs's size.
		Archive archive = (Archive) getSessionAttribute(session, "archive");
		if (archive == null || archive.getAllDocs() == null)
			return false;
		Collection<Document> docs = (Collection<Document>) getSessionAttribute(session, "emailDocs");
		if (docs == null)
			return false;
		return (docs.size() < archive.getAllDocs().size());
	}


	public static void setSessionConfigParam(HttpServletRequest request)
	{
		String key = request.getParameter("key");
		String value = request.getParameter("value");
		log.info("setting session var: " + key + " to " + value);
		if (key == null)
			return;

		if (!runningOnLocalhost(request) && ("user".equalsIgnoreCase(key) || "userKey".equalsIgnoreCase(key) || "cacheDir".equalsIgnoreCase(key)))
		{
			log.warn("Dropping attempt to set taboo session var: " + key + " = " + value + " -- this can be dangerous in a hosted environment.");
			return;
		}
		request.getSession().setAttribute(key, value);
	}



	/** serve up a file from the cache_dir */
	public static void serveBlob(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		HttpSession session = request.getSession();
		String filename = request.getParameter("file");
		filename = convertRequestParamToUTF8(filename);

		//get archiveID from the request parameter and then get the archive. It must be present
		//use its baseDir.
		Archive archive = JSPHelper.getArchive(request);
		assert archive!=null: new AssertionError("ArchiveID not passed to serveAttachment.jsp");
		String baseDir = archive.baseDir;

		if (filename.indexOf(".." + File.separator) >= 0) // avoid file injection!
		{
			log.warn("File traversal attack !? Disallowing serveFile for illegal filename: " + filename);
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// could check if user is authorized here... or get the userKey directly from session

		String filePath = baseDir + File.separator + Archive.BLOBS_SUBDIR + File.separator + filename;
		writeFileToResponse(session, response, filePath, true /* asAttachment */);
	}

	/** serve up a file from the cache_dir */
	public static void serveImage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		HttpSession session = request.getSession();
		String filename = request.getParameter("file");
		filename = convertRequestParamToUTF8(filename);
		String type = request.getParameter("type");
		String baseDir = (String) getSessionAttribute(session, "cacheDir");
		if (filename.indexOf(".." + File.separator) >= 0) // avoid file injection!
		{
			log.warn("File traversal attack !? Disallowing serveFile for illegal filename: " + filename);
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// could check if user is authorized here... or get the userKey directly from session

		String filePath = baseDir + File.separator + Archive.IMAGES_SUBDIR + File.separator + filename;
		writeFileToResponse(session, response, filePath, true /* asAttachment */);
	}


	/** serve up a file from the temp dir, mainly used for serving exported mbox files*/
	public static void serveTemp(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		HttpSession session = request.getSession();
		String filename = request.getParameter("file");
		filename = convertRequestParamToUTF8(filename);

		//get archiveID from the request parameter and then get the archive. It must be present
		//use its baseDir.
		Archive archive = JSPHelper.getArchive(request);
		assert archive!=null: new AssertionError("ArchiveID not passed to serveTemp.jsp");
		String baseDir = archive.baseDir;

		if (filename.contains(".." + File.separator)) // avoid file injection!
		{
			log.warn("File traversal attack !? Disallowing serveFile for illegal filename: " + filename);
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// could check if user is authorized here... or get the userKey directly from session

		String filePath = baseDir + File.separator + "tmp" + File.separator + filename;
		writeFileToResponse(session, response, filePath, true /* asAttachment */);
	}

	public static void writeFileToResponse(HttpSession session, HttpServletResponse response, String filePath, boolean asAttachment) throws IOException
	{
		// Decode the file name (might contain spaces and on) and prepare file object.
		File file = new File(filePath);

		// Check if file actually exists in filesystem.
		if (!file.exists())
		{
			log.warn("Returing 404, serveFile can't find file: " + filePath);

			// Do your thing if the file appears to be non-existing.
			// Throw an exception, or send 404, or show default/warning page, or just ignore it.
			response.sendError(HttpServletResponse.SC_NOT_FOUND); // 404.
			return;
		}

		// Get content type by filename.

		String contentType = session.getServletContext().getMimeType(file.getName());

		// If content type is unknown, then set the default value.
		// For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
		// To add new content types, add new mime-mapping entry in web.xml.
		if (contentType == null) {
			contentType = "application/octet-stream";
		}

		// Init servlet response.
		int DEFAULT_BUFFER_SIZE = 100000;
		response.reset();
		response.setBufferSize(DEFAULT_BUFFER_SIZE);
		response.setContentType(contentType);

		// not really sure why this is needed, but we have to ensure that these headers are not present when rendering e.g. xwordImage (directly rendered into web browser, instead of piclens)
		if (asAttachment)
		{
			response.setHeader("Content-Length", String.valueOf(file.length()));
			response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
		}
		// Prepare streams.
		BufferedInputStream input = null;
		BufferedOutputStream output = null;

		try {
			// Open streams.
			input = new BufferedInputStream(new FileInputStream(file), DEFAULT_BUFFER_SIZE);
			output = new BufferedOutputStream(response.getOutputStream(), DEFAULT_BUFFER_SIZE);

			// Write file contents to response.
			byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
			int length;
			while ((length = input.read(buffer)) > 0) {
				output.write(buffer, 0, length);
			}
		} finally {
			// Gently close streams.
			Util.close(output);
			Util.close(input);
		}
	}

	/**
	 * makes the page un$able. warning: must be called before any part of the
	 * response body is committed
	 */
	public static void setPageUncacheable(HttpServletResponse response)
	{
		// see http://stackoverflow.com/questions/5139785/how-to-prevent-the-result-of-servlets-from-being-cached
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Expires", "-1");

		// Set IE extended HTTP/1.1 no-cache headers (use addHeader).
		response.addHeader("Cache-Control", "post-check=0, pre-check=0");

		// Set standard HTTP/1.0 no-cache header.
		response.setDateHeader("Expires", 0); //prevent caching at the proxy server
	}

	public static Collection<DatedDocument> filterByDate(Collection<DatedDocument> docs, String dateSpec)
	{
		List<DatedDocument> selectedDocs = new ArrayList<DatedDocument>();
		if (Util.nullOrEmpty(dateSpec))
			return docs;

		List<DateRangeSpec> rangeSpecs = SloppyDates.parseDateSpec(dateSpec);
		for (DatedDocument d : docs)
		{
			for (DateRangeSpec spec : rangeSpecs)
				if (spec.satisfies(d.date))
				{
					selectedDocs.add(d);
					break;
				}
		}
		return selectedDocs;
	}
}
