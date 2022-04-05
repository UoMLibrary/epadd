//Example: ingest
//        Record the MBOX files which were ingested (file level events: MBOX files).
//        This file was ingested locally, here is the fixity check, it was a success
//

//For most events we are just capturing that something has happened in the system, not worrying about the file level operations.


        package edu.stanford.muse.epaddpremis;

import edu.stanford.epadd.Version;
import edu.stanford.muse.util.Util;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "premis")
public class EpaddPremis {

    public EpaddPremis(String pathToFile) throws JAXBException, IOException {
        path = pathToFile;
        setAgent();
        printToFile();
    }

    public EpaddPremis()
    {
    }
    @XmlTransient
    private String path;

    @XmlElement(name = "agent")
    private Agent agent;

    public void setAgent() {
        agent = new Agent("ePADD", "ePADD", "softeware", Version.version);
    }

    @XmlElement(name = "event")
    private List<EpaddEvent> epaddEvents = new ArrayList<>();


    public void createEvent(EpaddEvent.EventType eventType, String eventDetailInformation, String outcome)
    {
        epaddEvents.add(new EpaddEvent(eventType, eventDetailInformation, outcome));
        printToFile();
    }

    public void createEvent(EpaddEvent.EventType eventType, String eventDetailInformation, String outcome, String linkingObjectIdentifierType, String linkingObjectIdentifierValue)
    {
        epaddEvents.add(new EpaddEvent(eventType, eventDetailInformation, outcome, linkingObjectIdentifierType, linkingObjectIdentifierValue));
        printToFile();
    }

    public void createEvent(JSONObject eventJsonObject) {
        epaddEvents.add(new EpaddEvent(eventJsonObject));
        printToFile();
    }

    public static EpaddPremis createFromFile(String path) {
        EpaddPremis epaddPremis = null;
        try {
            Path file = Paths.get(path);
            JAXBContext jaxbContext = JAXBContext.newInstance(EpaddPremis.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            InputStream inputStream = new FileInputStream(file.toFile());
            epaddPremis = (EpaddPremis) unmarshaller.unmarshal(inputStream);
            inputStream.close();
        }
        catch (Exception e)
        {
            Util.print_exception("Exception reading Premis XML file", e, LogManager.getLogger(EpaddPremis.class));
            System.out.println("Exception reading Premis XML file " + e);
        }
        if (epaddPremis != null)
        {
            epaddPremis.path = path;
        }
        return epaddPremis;
    }

    public void printToFile() {
        try {
            JAXBContext context = JAXBContext.newInstance(EpaddPremis.class);
            Marshaller mar = context.createMarshaller();
            mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            mar.marshal(this, new File(path));
        }
        catch (Exception e)
        {
            Util.print_exception("Exception printing Premis XML file", e, LogManager.getLogger(EpaddPremis.class));
            System.out.println("Exception printing Premis XML file " + e);
        }
    }
}
