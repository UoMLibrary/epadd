package edu.stanford.muse.epaddpremis;

import edu.stanford.epadd.Version;
import org.json.JSONObject;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@XmlType(name = "event")
public class EpaddEvent {

    public static class EventTypeAdapter extends XmlAdapter<String, EventType>
    {
        public String marshal(EventType eventType) {
            return eventType.toString();
        }
        public EventType unmarshal(String val) {
            return EventType.fromString(val);
        }
    }

    @XmlElement(name="eventType")
    @XmlJavaTypeAdapter(EventTypeAdapter.class)
    private EventType eventType;

    @XmlElement(name="eventIdentifierType")
    private final String eventIdentifierType = "UUID";

    @XmlElement(name="uuidString")
    private String uuidString;

    @XmlElement(name="eventDateTime")
    private String eventDateTime;

    @XmlElement(name="eventDetailInformation")
    private String eventDetailInformation;

    @XmlElement(name="linkingAgentIdentifier")
    private LinkingAgentIdentifier linkingAgentIdentifier;

    @XmlElement
    private LinkingObjectIdentifier linkingObjectIdentifier;

    @XmlElement
    private EventOutcomeInformation eventOutcomeInformation;

    public enum EventType {
        MBOX_INGEST("mbox ingest"), MBOX_EXPORT("mbox export"), NOT_RECOGNIZED("not recognized");

        private final String eventType;

        EventType(String eventType) {
            this.eventType = eventType;
        }
        public String toString()
        {
            return eventType;
        }

        public static EventType fromString(String text) {
            for (EventType e : EventType.values()) {
                if (e.eventType.equalsIgnoreCase(text)) {
                    return e;
                }
            }
            return NOT_RECOGNIZED;
        }
    }

    private static class EventOutcomeInformation {

        @XmlElement
        private String outcome;

        private EventOutcomeInformation()
        {
        }

        private EventOutcomeInformation(String value) {
            outcome = value;
        }
    }

    public EpaddEvent()
    {
    }

    private static class LinkingObjectIdentifier
    {
        private LinkingObjectIdentifier(){}

        private LinkingObjectIdentifier(String type, String value)
        {
            linkingObjectIdentifierType = type;
            linkingObjectIdentifierValue = value;
        }
        @XmlElement
        private String linkingObjectIdentifierType;

        @XmlElement
        private String linkingObjectIdentifierValue;
    }

    private static class LinkingAgentIdentifier {

        @XmlElement
        private final String linkingAgentIdentifierType = "local";

        @XmlElement
        private String linkingAgentIdentifierValue;

        @XmlElement
        private final LinkingAgentRole linkingAgentRole = new LinkingAgentRole();

        private static class LinkingAgentRole {
            private LinkingAgentRole()
            {
            }

            @XmlAttribute
            private final String authority="eventRelatedAgentRole";

            @XmlAttribute
            private final String authorityURI="http://id.loc.gov/vocabulary/preservation/eventRelatedAgentRole";

            @XmlAttribute
            private final String valueURI="http://id.loc.gov/vocabulary/preservation/eventRelatedAgentRole/exe";

            @XmlValue
            private final String value = "executing program";
        }
        private LinkingAgentIdentifier()
        {
            linkingAgentIdentifierValue = "";
            setEpaddVersion();
            setJavaVersion();
            setOs();
        }

        private void setEpaddVersion()
        {
            linkingAgentIdentifierValue += ("ePADD - " + Version.version);
        }

        private void setOs()
        {
            linkingAgentIdentifierValue += (" - " + System.getProperty("os.name"));
        }

        private void setJavaVersion()
        {
            linkingAgentIdentifierValue += (" - running in " + System.getProperty("java.version"));
        }


        private LinkingAgentIdentifier(String value) {
            this();
            value = value;
        }
    }

    public EpaddEvent(EventType eventType, String eventDetailInformation, String outcome)
    {
        eventOutcomeInformation = new EventOutcomeInformation(outcome);
        linkingAgentIdentifier = new LinkingAgentIdentifier();
        this.eventType = eventType;
        this.eventDetailInformation = eventDetailInformation;
        setValues();
    }

    public EpaddEvent(EventType eventType, String eventDetailInformation, String outcome, String linkingObjectIdentifierType, String linkingObjectIdentifierValue)
    {
        this(eventType, eventDetailInformation, outcome);
        linkingObjectIdentifier = new LinkingObjectIdentifier(linkingObjectIdentifierType, linkingObjectIdentifierValue);
    }

    public EpaddEvent(JSONObject eventJsonObject) {
        eventOutcomeInformation = new EventOutcomeInformation(eventJsonObject.getString("outcome"));
        linkingAgentIdentifier = new LinkingAgentIdentifier();
        String eventString = eventJsonObject.getString("eventType");
        EpaddEvent.EventType eventType;
        if (eventString == null || eventString.isEmpty()) {
            eventType = EpaddEvent.EventType.NOT_RECOGNIZED;
        } else {
            eventType = EpaddEvent.EventType.fromString(eventString);
        }
        this.eventType = eventType;
        this.eventDetailInformation = eventJsonObject.getString("eventDetailInformation");
        setValues();
    }

    private void setValues() {
        generateAndSetUuid();
        setEventDateTimeToNow();
    }

    private void generateAndSetUuid() {
        uuidString = UUID.randomUUID().toString();
    }

    private void setEventDateTimeToNow()
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
        eventDateTime = ZonedDateTime.now().format(formatter);
    }
}
