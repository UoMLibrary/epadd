package edu.stanford.muse.index;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.stanford.muse.AddressBookManager.AddressBook;
import edu.stanford.muse.AddressBookManager.CorrespondentAuthorityMapper;
import edu.stanford.muse.AnnotationManager.AnnotationManager;
import edu.stanford.muse.Config;
import edu.stanford.muse.LabelManager.LabelManager;
import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.email.MuseEmailFetcher;
import edu.stanford.muse.ie.variants.EntityBook;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import edu.stanford.muse.webapp.ModeConfig;
import edu.stanford.muse.webapp.SimpleSessions;
import gov.loc.repository.bagit.creator.BagCreator;
import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.hash.StandardSupportedAlgorithms;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.LockObtainFailedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ArchiveReaderWriter{

    public static final String SESSION_SUFFIX = ".archive.v2"; // all session files end with .session
    private static Log log	= LogFactory.getLog(SimpleSessions.class);
    //private static String SESSIONS_DIR = null;
    private static String MUSE_DIRNAME = ".muse"; // clients might choose to override this

    public static String CACHE_BASE_DIR = null; // overruled (but not overwritten) by session's attribute "cacheDir"
    public static String CACHE_DIR = null; // overruled (but not overwritten) by session's attribute "cacheDir"

    static {
        MUSE_DIRNAME = SimpleSessions.getVarOrDefault("muse.defaultArchivesDir", System.getProperty("user.home") + File.separator + ".muse");
        CACHE_BASE_DIR = SimpleSessions.getVarOrDefault("muse.dir.cache_base", MUSE_DIRNAME);
        //modified the CACHE_DIR in v6 to hava path as user/data
        CACHE_DIR      = SimpleSessions.getVarOrDefault("muse.dir.cache"  , ArchiveReaderWriter.CACHE_BASE_DIR + File.separator + "user"); // warning/todo: this "-D" not universally honored yet, e.g., user_key and fixedCacheDir in MuseEmailFetcher.java
        //SESSIONS_DIR   = getVarOrDefault("muse.dir.sessions", CACHE_DIR + File.separator + Archive.SESSIONS_SUBDIR); // warning/todo: this "-D" not universally honored yet, e.g., it is hard-coded again in saveSession() (maybe saveSession should actually use getSessinoDir() rather than basing it on cacheDir)
    }

    //#############################################Start: Weak reference cache for the archive object and archive ID################################
    // an archive in a given dir should be loaded only once into memory.
    // this map stores the directory -> archive mapping.
    private static LinkedHashMap<String, WeakReference<Archive>> globaldirToArchiveMap = new LinkedHashMap<>();
    private static LinkedHashMap<String,Archive> globalArchiveIDToArchiveMap = new LinkedHashMap<>();
    private static LinkedHashMap<Archive,String> globalArchiveToArchiveIDMap = new LinkedHashMap<>();

    //#############################################End: Weak reference cache for the archive object and archive#####################################

    //#############################################Start: Reading/loading an archive bag###########################################################
    /**
     * loads session from the given filename, and returns the map of loaded
     * attributes.
     * if readOnly is false, caller MUST make sure to call packIndex.
     * baseDir is Indexer's baseDir (path before "indexes/")
     *
     * @throws IOException
     * @throws LockObtainFailedException
     * @throws CorruptIndexException
     * Change as on Nov 2017-
     * Earlier the whole archive was serialized and deserialized as one big entity. Now it is broken into
     * four main parts, Addressbook, entitybook, correspondentAuthorityMapper and the rest of the object
     * We save all these four components separately in saveArchive. Therefore while reading, we need to read
     * all those separately from appropriate files.
     */
    public static Map<String, Object> loadSessionAsMap(String filename, String baseDir, boolean readOnly) throws IOException
    {
        log.info("Loading session from file " + filename + " size: " + Util.commatize(new File(filename).length() / 1024) + " KB");

        ObjectInputStream ois = null;

        // keep reading till eof exception
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            ois = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(filename))));

            while (true)
            {
                String key = (String) ois.readObject();
                log.info("loading key: " + key);
                try {
                    Object value = ois.readObject();
                    if (value == null)
                        break;
                    result.put(key, value);
                } catch (InvalidClassException ice)
                {
                    log.error("Bad version for value of key " + key + ": " + ice + "\nContinuing but this key is not set...");
                } catch (ClassNotFoundException cnfe)
                {
                    log.error("Class not found for value of key " + key + ": " + cnfe + "\nContinuing but this key is not set...");
                }
            }
        } catch (EOFException eof) {
            log.info("end of session file reached");
        } catch (Exception e) {
            log.warn("Warning unable to load session: " + Util.stackTrace(e));
            result.clear();
        }

        if (ois != null)
            try {
                ois.close();
            } catch (Exception e) {
                Util.print_exception(e, log);
            }

        // need to set up sentiments explicitly -- now no need since lexicon is part of the session
        log.info("Memory status: " + Util.getMemoryStats());

        Archive archive = (Archive) result.get("archive");
        // no groups in public mode
        if (archive != null)
        {
            /*
                Read other three modules of Archive object which were set as transient and hence did not serialize.
                */
            //file path names of addressbook, entitybook and correspondentAuthorityMapper data.
            String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;

            String addressBookPath = dir + File.separatorChar + Archive.ADDRESSBOOK_SUFFIX;
            String entityBookPath = dir + File.separatorChar + Archive.ENTITYBOOK_SUFFIX;
            String cAuthorityPath =  dir + File.separatorChar + Archive.CAUTHORITYMAPPER_SUFFIX;
            String labMapDirPath= dir + File.separatorChar + Archive.LABELMAPDIR;
            String annotationMapPath = dir + File.separatorChar + Archive.ANNOTATION_SUFFIX;
            String blobNormalizationMapPath = dir + File.separatorChar + Archive.BLOBLNORMALIZATIONFILE_SUFFIX;

            //Error handling: For the case when epadd is running first time on an archive that was not split it is possible that
            //above three files are not present. In that case start afresh with importing the email-archive again in processing mode.
            if(!(new File(addressBookPath).exists()) || !(new File(entityBookPath).exists()) || !(new File(cAuthorityPath).exists())){
                result.put("archive", null);
                return result;
            }


            /////////////////AddressBook////////////////////////////////////////////
           AddressBook ab = readAddressBook(addressBookPath);
            archive.addressBook = ab;

            ////////////////EntityBook/////////////////////////////////////
            EntityBook eb = readEntityBook(entityBookPath);
            archive.setEntityBook(eb);
            ///////////////CorrespondentAuthorityMapper/////////////////////////////
            CorrespondentAuthorityMapper cmapper = null;
            cmapper = CorrespondentAuthorityMapper.readObjectFromStream(cAuthorityPath);

            archive.correspondentAuthorityMapper = cmapper;
            /////////////////Label Mapper/////////////////////////////////////////////////////
            LabelManager labelManager = readLabelManager(labMapDirPath);
            archive.setLabelManager(labelManager);

            ///////////////Annotation Manager///////////////////////////////////////////////////////
            AnnotationManager annotationManager = AnnotationManager.readObjectFromStream(annotationMapPath);
            archive.setAnnotationManager(annotationManager);
            ///////////////Processing metadata////////////////////////////////////////////////
            // override the PM inside the archive with the one in the PM file
            //update: since v5 no pm will be inside the archive.
            // this is useful when we import a legacy archive into processing, where we've updated the pm file directly, without updating the archive.
            try {
                archive.collectionMetadata = readCollectionMetadata(baseDir);
            } catch (Exception e) {
                Util.print_exception ("Error trying to read processing metadata file", e, log);
            }
            /////////////////////Blob Normalization map (IF exists)//////////////////////////////////////////////////////
            if(new File(blobNormalizationMapPath).exists()) {
                archive.getBlobStore().setNormalizationMap(blobNormalizationMapPath);
            }
            /////////////////////////////Done reading//////////////////////////////////////////////////////
            // most of this code should probably move inside Archive, maybe a function called "postDeserialized()"
            archive.postDeserialized(baseDir, readOnly);
            result.put("emailDocs", archive.getAllDocs());
            archive.assignThreadIds();
        }

        return result;
    }

    public static LabelManager readLabelManager(String labMapDirPath) {
        LabelManager labelManager = null;
        try {
            labelManager = LabelManager.readObjectFromStream(labMapDirPath);
        } catch (Exception e) {
            Util.print_exception ("Exception in reading label manager from archive, assigning a new label manager", e, log);
            labelManager = new LabelManager();
        }
        return labelManager;

    }

    public static EntityBook readEntityBook(String entityBookPath) {

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(entityBookPath));
            EntityBook eb = EntityBook.readObjectFromStream(br);
            br.close();
            return eb;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static AddressBook readAddressBook(String addressBookPath) {

        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(addressBookPath));
            AddressBook ab = AddressBook.readObjectFromStream(br);
            br.close();
            return ab;
        } catch (FileNotFoundException e) {

            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }


    }

    /**
     * reads name.processing.metadata from the given basedir. should be used
     * when quick archive metadata is needed without loading the actual archive
     */
    public static Archive.CollectionMetadata readCollectionMetadata(String baseDir) {
        String processingFilename = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR + File.separatorChar + Config.COLLECTION_METADATA_FILE;
        try (Reader reader = new FileReader(processingFilename)) {
            Archive.CollectionMetadata metadata = new Gson().fromJson(reader, Archive.CollectionMetadata.class);
            return metadata;
        } catch (Exception e) {
            Util.print_exception("Unable to read processing metadata from file" + processingFilename, e, log);
        }
        return null;
    }

    //#######################################End: Loading/reading an archive bag#####################################################################

    //#######################################Start: Saving the archive (flat directory or bag) depending upon the mode argument#############################
    //incremental save is used when a module (label,annotation etc) are saved from inside a loaded archive bag. We need to update the bag metadata
    //to reflect these changes otherwise the checksum calculation fails##############################################################################

    /** saves the archive in the current session to the cachedir */
    public static boolean saveArchive(Archive archive, Archive.Save_Archive_Mode mode) throws IOException
    {
        assert archive!=null : new AssertionError("No archive to save.");
        // String baseDir = (String) session.getAttribute("cacheDir");
        return saveArchive(archive.baseDir, "default", archive,mode);
    }

    /** saves the archive in the current session to the cachedir. note: no blobs saved. */
    /* mode attributes select if this archive was already part of a bag or is a first time creation. Based on this flag the ouptput directory
    changes. In case of incremental bag update, the files will be in basedir/data/ subfolder whereas in case of fresh creation the files will be in
    basedir.
     */
    public static boolean saveArchive(String baseDir, String name, Archive archive, Archive.Save_Archive_Mode mode) throws IOException
    {
        /*log.info("Before saving the archive checking if it is still in good shape");
        archive.Verify();*/
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        new File(dir).mkdirs(); // just to be safe
        String filename = dir + File.separatorChar + name + SESSION_SUFFIX;
        log.info("Saving archive to (session) file " + filename);
        /*//file path names of addressbook, entitybook and correspondentAuthorityMapper data.
        String addressBookPath = dir + File.separatorChar + Archive.ADDRESSBOOK_SUFFIX;
        String entityBookPath = dir + File.separatorChar + Archive.ENTITYBOOK_SUFFIX;
        String cAuthorityPath =  dir + File.separatorChar + Archive.CAUTHORITYMAPPER_SUFFIX;
        */
        recalculateCollectionMetadata(archive);



        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(filename))))) {
            oos.writeObject("archive");
            oos.writeObject(archive);
        } catch (Exception e1) {
            Util.print_exception("Failed to write archive: ", e1, log);
        }

        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE){
            archive.updateFileInBag(filename,archive.baseDir);
        }
        //Now write modular transient fields to separate files-
        //By Dec 2017 there are three transient fields which will be saved and loaded separately
        //1. AddressBook -- Stored in a gzip file with name in the same `	directory as of archive.
        //2. EntityBook
        //3. CorrespondentAuthorityMapper
        //Before final release of v5 in Feb 2018, modularize annotation out of archive.
        /////////////////AddressBook Writing -- In human readable form ///////////////////////////////////
        saveAddressBook(archive,mode);
        ////////////////EntityBook Writing -- In human readable form/////////////////////////////////////
        saveEntityBook(archive,mode);
        ///////////////CAuthorityMapper Writing-- Serialized///////////////////////////////
        saveCorrespondentAuthorityMapper(archive,mode);
        //////////////LabelManager Writing -- Serialized//////////////////////////////////
        saveLabelManager(archive,mode);

        //////////////AnnotationManager writing-- In human readable form/////////////////////////////////////
        saveAnnotations(archive,mode);
        saveCollectionMetadata(archive,mode);

/*        //if normalizationInfo is present save that too..
        if(archive.getBlobStore().getNormalizationMap()!=null){
            saveNormalizationMap(archive,mode);
        }*/
//if archivesave mode is freshcreation then create a bag around basedir and set bag as this one..
        if(mode== Archive.Save_Archive_Mode.FRESH_CREATION){
            StandardSupportedAlgorithms algorithm = StandardSupportedAlgorithms.MD5;
            boolean includeHiddenFiles = false;
            try {
                archive.close();


                //First copy the content of archive.baseDir + "/data" to archive.baseDir and then create an in place bag.
                //Why so complicated? Because we wanted to have a uniform directory structure of archive irrespective of the fact whether it is
                //in a bag or not. That structure is 'archive.baseDir + "/data" folder'
                File tmp = Util.createTempDirectory();
                tmp.delete();

                //It seems that if indexer kept the file handles open then move directory failed on windows because of the lock held on those file
                //therefore call archive.close() before moving stuff around
                //archive.close();
                FileUtils.moveDirectory(Paths.get(archive.baseDir+File.separatorChar+Archive.BAG_DATA_FOLDER).toFile(),tmp.toPath().toFile());
                //Files.copy(Paths.get(userDir+File.separatorChar+Archive.BAG_DATA_FOLDER),tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);
                File wheretocopy = Paths.get(archive.baseDir).toFile();
                Util.deleteDir(wheretocopy.getPath(),log);

                FileUtils.moveDirectory(tmp.toPath().toFile(),wheretocopy);

                Bag bag = BagCreator.bagInPlace(Paths.get(archive.baseDir), Arrays.asList(algorithm), includeHiddenFiles);
                archive.openForRead();
                archive.setArchiveBag(bag);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

        }else {
            archive.close();

            // re-open for reading
            archive.openForRead();

        }
        return true;
    }


    /*
    From the save button on top nav-bar we should trigger only incremental save of mutable data like addressbook, labelmanager, etc.
    A smarter way will be to save only those parts which changed. This will require some flag to track unchanged data.-- @TODO
     */
    public static void saveMutable_Incremental(Archive archive){
        saveAddressBook(archive, Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);
        ////////////////EntityBook Writing -- In human readable form/////////////////////////////////////
        saveEntityBook(archive,Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);
        ///////////////CAuthorityMapper Writing-- Serialized///////////////////////////////
        saveCorrespondentAuthorityMapper(archive,Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);
        //////////////LabelManager Writing -- Serialized//////////////////////////////////
        saveLabelManager(archive,Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);

        //////////////AnnotationManager writing-- In human readable form/////////////////////////////////////
        saveAnnotations(archive,Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);
        //should we recalculate collection metadata as well??
        recalculateCollectionMetadata(archive);
        saveCollectionMetadata(archive,Archive.Save_Archive_Mode.INCREMENTAL_UPDATE);

    }

    public static void recalculateCollectionMetadata(Archive archive) {
        if (archive.collectionMetadata == null)
            archive.collectionMetadata = new Archive.CollectionMetadata();

        archive.collectionMetadata.timestamp = new Date().getTime();
        archive.collectionMetadata.tz = TimeZone.getDefault().getID();
        archive.collectionMetadata.nDocs = archive.getAllDocs().size();
        archive.collectionMetadata.nUniqueBlobs = archive.blobStore.uniqueBlobs.size();

        int totalAttachments = 0, images = 0, docs = 0, others = 0, sentMessages = 0, receivedMessages = 0, hackyDates = 0;
        Date firstDate = null, lastDate = null;

        for (Document d: archive.getAllDocs()) {
            if (!(d instanceof EmailDocument))
                continue;
            EmailDocument ed = (EmailDocument) d;
            if (ed.date != null) {
                if (ed.hackyDate)
                    hackyDates++;
                else {
                    if (firstDate == null || ed.date.before(firstDate))
                        firstDate = ed.date;
                    if (lastDate == null || ed.date.after(lastDate))
                        lastDate = ed.date;
                }
            }
            int sentOrReceived = ed.sentOrReceived(archive.addressBook);
            if ((sentOrReceived & EmailDocument.SENT_MASK) != 0)
                sentMessages++;
            if ((sentOrReceived & EmailDocument.RECEIVED_MASK) != 0)
                receivedMessages++;

            if (!Util.nullOrEmpty(ed.attachments))
            {
                totalAttachments += ed.attachments.size();
                for (Blob b: ed.attachments)
                    if (!Util.nullOrEmpty(archive.getBlobStore().get_URL_Normalized(b)))
                    {
                        if (Util.is_image_filename(archive.getBlobStore().get_URL_Normalized(b)))
                            images++;
                        else if (Util.is_doc_filename(archive.getBlobStore().get_URL_Normalized(b)))
                            docs++;
                        else
                            others++;
                    }
            }
        }

        archive.collectionMetadata.firstDate = firstDate;
        archive.collectionMetadata.lastDate = lastDate;
        archive.collectionMetadata.nIncomingMessages = receivedMessages;
        archive.collectionMetadata.nOutgoingMessages = sentMessages;
        archive.collectionMetadata.nHackyDates = hackyDates;

        archive.collectionMetadata.nBlobs = totalAttachments;
        archive.collectionMetadata.nUniqueBlobs = archive.blobStore.uniqueBlobs.size();
        archive.collectionMetadata.nImageBlobs = images;
        archive.collectionMetadata.nDocBlobs = docs;
        archive.collectionMetadata.nOtherBlobs = others;
    }

    /*public static void saveNormalizationMap(Archive archive, Archive.Save_Archive_Mode mode){
        String baseDir = archive.baseDir;
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        new File(dir).mkdirs(); // just to be safe
        //file path name of normalization file.
        String normalizationPath = dir + File.separatorChar + Archive.BLOBLNORMALIZATIONFILE_SUFFIX;
        archive.getBlobStore().writeNormalizationMap(normalizationPath);
        //if this was an incremental update , we need to update the bag's metadata as well..
        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE)
            archive.updateFileInBag(normalizationPath,baseDir);

    }
*/
    public static void saveAddressBook(Archive archive, Archive.Save_Archive_Mode mode){

        String baseDir = archive.baseDir;
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        new File(dir).mkdirs(); // just to be safe
        //file path name of addressbook data.
        String addressBookPath = dir + File.separatorChar + Archive.ADDRESSBOOK_SUFFIX;
        log.info("Saving addressBook to file " + addressBookPath);
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(addressBookPath));
            archive.addressBook.writeObjectToStream (bw, false, false);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //if this was an incremental update in addressbook, we need to update the bag's metadata as well..
        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE)
            archive.updateFileInBag(addressBookPath,baseDir);

        //update the summary of addressbook (counts etc used on correspondent listing page).
        Archive.cacheManager.cacheCorrespondentListing(ArchiveReaderWriter.getArchiveIDForArchive(archive));

    }

    public static void saveEntityBook(Archive archive, Archive.Save_Archive_Mode mode){
        String baseDir = archive.baseDir;
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        //file path name of entitybook
        String entityBookPath = dir + File.separatorChar + Archive.ENTITYBOOK_SUFFIX;
        log.info("Saving entity book to file " + entityBookPath);

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(entityBookPath));
            archive.getEntityBook().writeObjectToStream(bw);
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        //if this was an incremental update in entitybook, we need to update the bag's metadata as well..
        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE)
            archive.updateFileInBag(entityBookPath,baseDir);


    }

    public static void saveCorrespondentAuthorityMapper(Archive archive, Archive.Save_Archive_Mode mode){
        String baseDir = archive.baseDir;
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        //file path name of entitybook
        String cAuthorityPath = dir + File.separatorChar + Archive.CAUTHORITYMAPPER_SUFFIX;
        new File(cAuthorityPath).mkdir();
        log.info("Saving correspondent Authority mappings to directory" + cAuthorityPath);

        try {
            archive.getCorrespondentAuthorityMapper().writeObjectToStream(cAuthorityPath);
        } catch (ParseException | IOException | ClassNotFoundException e) {
            log.warn("Exception while writing correspondent authority files"+e.getMessage());
            e.printStackTrace();
        }

        //if this was an incremental update in authority mapper, we need to update the bag's metadata as well..
        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE)
            archive.updateFileInBag(cAuthorityPath,baseDir);


    }

    public static void saveLabelManager(Archive archive, Archive.Save_Archive_Mode mode){
        String baseDir = archive.baseDir;
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        //file path name of labelMap file
        String labMapDir = dir + File.separatorChar + Archive.LABELMAPDIR;
        new File(labMapDir).mkdir();//create dir if not exists.
        log.info("Saving label mapper to directory " + labMapDir);
        //TEMP: create a map of signature to docid and pass it to Labelmanager.write method.. This is done to expand the csv file's signature to include
        //the signature of the document as well (for readability and debuggability)
        Map<String,String> docidToSignature = new LinkedHashMap<>();
        for(Document d: archive.getAllDocsAsSet()){
            EmailDocument ed = (EmailDocument)d;
            docidToSignature.put(ed.getUniqueId(),ed.getSignature());
        }
        archive.getLabelManager().writeObjectToStream(labMapDir,docidToSignature);

        //if this was an incremental update in label manager, we need to update the bag's metadata as well..
        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE)
            archive.updateFileInBag(labMapDir,baseDir);

    }

    public static void saveAnnotations(Archive archive, Archive.Save_Archive_Mode mode){
        String baseDir = archive.baseDir;
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;

        String annotationcsv = dir + File.separatorChar + Archive.ANNOTATION_SUFFIX;
        Map<String,String> docidToSignature = new LinkedHashMap<>();
        for(Document d: archive.getAllDocsAsSet()){
            EmailDocument ed = (EmailDocument)d;
            docidToSignature.put(ed.getUniqueId(),ed.getSignature());
        }
        archive.getAnnotationManager().writeObjectToStream(annotationcsv,docidToSignature);

        //if this was an incremental update in annotation, we need to update the bag's metadata as well..
        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE)
            archive.updateFileInBag(annotationcsv,baseDir);


    }



    /**
* writes name.processing.metadata to the given basedir. should be used
* when quick archive metadata is needed without loading the actual archive
* basedir is up to /.../sessions
     * v5- Instead of serializing now this data gets stored in json format.
*/
    public static void saveCollectionMetadata(Archive.CollectionMetadata cm, String basedir){
        String dir = basedir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        String processingFilename = dir + File.separatorChar + Config.COLLECTION_METADATA_FILE;

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        FileWriter fwriter = null;
        try {
            fwriter = new FileWriter(processingFilename);
            gson.toJson(cm,fwriter);
        } catch (IOException e) {
            Util.print_exception("Unable to write processing metadata", e, log);
            e.printStackTrace();
        }finally {
            if(fwriter!=null){
                try {
                    fwriter.close();
                } catch (IOException e) {
                    Util.print_exception("Unable to write processing metadata", e, log);
                    e.printStackTrace();
                }
            }
        }

    }

    /*
Following variant is used when saving an archive. Here an extra argument, mode is passed to denote if it is a fresh archive creation (legacy folder structure
at the top) or an update in the file present in the archive bag.
*/
public static void saveCollectionMetadata(Archive archive, Archive.Save_Archive_Mode mode)
{
        Archive.CollectionMetadata cm = archive.collectionMetadata;
        String baseDir = archive.baseDir;
        String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.SESSIONS_SUBDIR;
        String processingFilename = dir + File.separatorChar + Config.COLLECTION_METADATA_FILE;

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        FileWriter fwriter = null;
        try {
            fwriter = new FileWriter(processingFilename);
            gson.toJson(cm,fwriter);
        } catch (IOException e) {
            Util.print_exception("Unable to write processing metadata", e, log);
            e.printStackTrace();
        }finally {
            if(fwriter!=null){
                try {
                    fwriter.close();
                } catch (IOException e) {
                    Util.print_exception("Unable to write processing metadata", e, log);
                    e.printStackTrace();
                }
            }
        }


        //if this was an incremental update in collection metadata, we need to update the bag's metadata as well..
        if(mode== Archive.Save_Archive_Mode.INCREMENTAL_UPDATE)
            archive.updateFileInBag(processingFilename,baseDir);

    }

    //#################################Start: Saving the archive (flat directory or bag) depending upon the mode argument####################

    /** VIP method. Should be the single place to load an archive from disk.
* loads an archive from the given directory. always re-uses archive objects loaded from the same directory.
     * this is fine when:
     * - running single-user
     * - running discovery mode epadd, since a single archive should be loaded only once.
     * - even in a hosted mode with different archives in simultaneous play, where different people have their own userKeys and therefore different dirs.
     * It may NOT be fine if  multiple people are operating on their different copies of an archive loaded from the same place. Don't see a use-case for this right now.
     * if you don't like that, tough luck.
     * return the archive, or null if it doesn't exist. */
    public static Archive readArchiveIfPresent(String baseDir) {
        //check if a valid bag in basedir.
        Bag archiveBag=Archive.readArchiveBag(baseDir);
        if(archiveBag==null)
            return null;

        //else, the bag has been verified.. now load the content.
        String archiveFile = baseDir + File.separator +  Archive.BAG_DATA_FOLDER+ File.separator + Archive.SESSIONS_SUBDIR + File.separator + "default" + SESSION_SUFFIX;
        if (!new File(archiveFile).exists()) {
            return null;
        }

        /*synchronized (globaldirToLoadCountMap) {
            Integer loadCount = globaldirToLoadCountMap.get(baseDir);
            int newCount = (loadCount == null) ? 1 : loadCount + 1;
            globaldirToLoadCountMap.put(baseDir, newCount);
            log.info ("Since server start, the archive: " + archiveFile + " has been (attempted to be) loaded " + Util.pluralize(newCount, "time"));
        }*/

        try {
            // locking the global dir might be inefficient if many people are loading different archives at the same time.
            // not a concern right now. it it does become one, locking a small per-dir object like archiveFile.intern(), along with a ConcurrenctHashMap might handle it.
            synchronized (globaldirToArchiveMap) {
                // the archive is wrapped inside a weak ref to allow the archive object to be collected if there are no references to it (usually the references
                // are in the user sessions).
                WeakReference<Archive> wra = getArchiveFromGlobalArchiveMap(baseDir);
                if (wra != null) {
                    Archive a = wra.get();
                    if (a != null) {
                        log.info("Great, could re-use loaded archive for dir: " + archiveFile + "; archive = " + a);
                        return a;
                    }
                }

                log.info("Archive not already loaded, reading from dir: " + archiveFile);
                Map<String, Object> map = loadSessionAsMap(archiveFile, baseDir, true);
                // read the session map, but only use archive
                Archive a = (Archive) map.get("archive");
                // could do more health checks on archive here
                if (a == null) {
                    log.warn ("Archive key is not present in archive file! The archive must be corrupted! directory:" + archiveFile);
                    return null;
                }
                a.setBaseDir(baseDir);



// no need to read archive authorized authorities, they will be loaded on demand from the legacy authorities.ser file
                addToGlobalArchiveMap(baseDir,a);
                //check if the loaded archive satisfy the verification condtiions. Call verify method on archive.
               /* JSPHelper.log.info("After reading the archive checking if it is in good shape");
                a.Verify();*/
                //assign bag to archive object.
                a.setArchiveBag(archiveBag);
                //now intialize the cache.1- correspondent, labels and entities
                Archive.cacheManager.cacheCorrespondentListing(ArchiveReaderWriter.getArchiveIDForArchive(a));
                Archive.cacheManager.cacheLexiconListing(getArchiveIDForArchive(a));
                Archive.cacheManager.cacheEntitiesListing(getArchiveIDForArchive(a));
                return a;

            }
        } catch (Exception e) {
            Util.print_exception("Error reading archive from dir: " + archiveFile, e, log);
            throw new RuntimeException(e);
        }
    }

    private static String removeTrailingSlashFromDirName(String dir){
        return new File(dir).getAbsolutePath();
    }

    public static void addToGlobalArchiveMap(String archiveDir, Archive archive){

        String s = removeTrailingSlashFromDirName(archiveDir);
        //add to globalDirmap
        globaldirToArchiveMap.put(s, new WeakReference<>(archive));
        //construct archive ID from the tail of the archiveFile (by sha1)
        String archiveID = Util.hash(s);
        globalArchiveIDToArchiveMap.put(archiveID,archive);
        //for reverse mapping
        globalArchiveToArchiveIDMap.put(archive,archiveID);

    }

    public static void removeFromGlobalArchiveMap(String archiveDir, Archive archive){
        String s = removeTrailingSlashFromDirName(archiveDir);
        globaldirToArchiveMap.remove(s);
        //remove from reverse mapping but first get the archive ID.
        String archiveID = globalArchiveToArchiveIDMap.get(archive);
        globalArchiveToArchiveIDMap.remove(archive);
        //remove from archiveID to archive mapping.
        if(!Util.nullOrEmpty(archiveID))
            globalArchiveIDToArchiveMap.remove(archiveID);
    }

    public static WeakReference<Archive> getArchiveFromGlobalArchiveMap(String archiveFile){
        String s = removeTrailingSlashFromDirName(archiveFile);
        return globaldirToArchiveMap.getOrDefault(s,null);
    }

    //If there is only one archive present in the global map then this funciton returns that
    //else it return null.
    public static Archive getDefaultArchiveFromGlobalArchiveMap(){
        if(globaldirToArchiveMap.size()==1)
            return globaldirToArchiveMap.values().iterator().next().get();
        else
            return null;
    }

    //This function returns the archiveID for the given archive
    public static String getArchiveIDForArchive(Archive archive){

        return globalArchiveToArchiveIDMap.getOrDefault(archive,null);
    }

    //This function returns the archive for the given archiveID
    public static Archive getArchiveForArchiveID(String archiveID){
        return globalArchiveIDToArchiveMap.getOrDefault(archiveID,null);
    }

    /**
     * reads from default dir (usually ~/.muse/user) and sets up cachedir,
     * archive vars.
     */
    public static Archive prepareAndLoadDefaultArchive(HttpServletRequest request) throws IOException
    {
        HttpSession session = request.getSession();

        // allow cacheDir parameter to override default location
        String dir = request.getParameter("cacheDir");
        if (Util.nullOrEmpty(dir))
            dir = CACHE_DIR;
        JSPHelper.log.info("Trying to read archive from " + dir);

        Archive archive = readArchiveIfPresent(dir);
        if (archive != null)
        {
            JSPHelper.log.info("Good, archive read from " + dir);

        /*	// always set these three together
            session.setAttribute("userKey", "user");
            session.setAttribute("cacheDir", dir);
            session.setAttribute("archive", archive);
*/
            // is this really needed?
            //Archive.prepareBaseDir(dir); // prepare default lexicon files etc.
/*
            Lexicon lex = archive.getLexicon("general");
            if (lex != null)
                session.setAttribute("lexicon", lex); // set up default general lexicon, so something is in the session as default lexicon (so facets can show it)
*/
        }
        return archive;
    }

    public static Archive prepareAndLoadArchive(MuseEmailFetcher m, HttpServletRequest request) throws IOException
    {

        // here's where we create a fresh archive
        String userKey = "user";
        /*if (ModeConfig.isServerMode())
        {
            // use existing key, or if not available, ask the fetcher which has the login email addresses for a key
            userKey = (String) session.getAttribute("userKey");
            if (Util.nullOrEmpty(userKey))
                userKey = m.getEffectiveUserKey();
            Util.ASSERT(!Util.nullOrEmpty(userKey)); // disaster if we got here without a valid user key
        }*/

        int i = new Random().nextInt();
        String randomPrefix = String.format("%08x", i);
        String archiveDir = ModeConfig.isProcessingMode()?Config.REPO_DIR_PROCESSING + File.separator + randomPrefix : CACHE_BASE_DIR + File.separator+userKey;
        //String archiveDir = Sessions.CACHE_BASE_DIR + File.separator + userKey;
        Archive archive = readArchiveIfPresent(archiveDir);

        if (archive != null) {
            JSPHelper.log.info("Good, existing archive found");
        } else {
            JSPHelper.log.info("Creating a new archive in " + archiveDir);
            archive = JSPHelper.preparedArchive(request, archiveDir, new ArrayList<>());
            //by this time the archive is created
            // add this to global maps archiveID->archive, archive->archiveID
            addToGlobalArchiveMap(archiveDir,archive);

        }

        Lexicon lex = archive.getLexicon("general");
    /*	if (lex != null)
            session.setAttribute("lexicon", lex); // set up default general lexicon, so something is in the session as default lexicon (so facets can show it)
    */
        return archive;
    }

    public static void main(String args[]){
        File tmp = null;
        try {
            FileUtils.moveDirectory(Paths.get("/home/chinmay/test").toFile(),Paths.get("/home/chinmay/test2").toFile());
            //Files.copy(Paths.get(userDir+File.separatorChar+Archive.BAG_DATA_FOLDER),tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);
            File wheretocopy = Paths.get("/home/chinmay/test2/test").toFile();
            Util.deleteDir(wheretocopy.getPath(),log);

            //FileUtils.moveDirectory(tmp.toPath().toFile(),wheretocopy);

        } catch (IOException e) {

        }
        tmp.delete();


    }

}