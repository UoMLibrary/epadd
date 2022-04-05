

@XmlSchema(
        namespace = "http://www.loc.gov/premis/v",
        elementFormDefault = XmlNsForm.QUALIFIED,
        xmlns = {
                @XmlNs(prefix = "premis", namespaceURI = "http://www.loc.gov/premis/v")
        })

package edu.stanford.muse.epaddpremis;


import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;