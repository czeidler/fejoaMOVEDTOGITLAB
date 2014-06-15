package org.fejoa.library.support;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.Writer;


public class ProtocolOutStream {
    private Document document = null;

    public ProtocolOutStream() throws ParserConfigurationException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        document = builder.newDocument();
    }

    public Element createElement(String name) {
        return document.createElement(name);
    }

    public void addElement(Element element) {
        document.appendChild(element);
    }

    public void write(Writer writer) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StreamResult result = new StreamResult(writer);
        DOMSource source = new DOMSource(document);
        transformer.transform(source, result);
        System.out.println(writer.toString());
    }

    static final public String IQ_GET = "get";
    static final public String IQ_SET = "set";
    static final public String IQ_RESULT = "result";
    static final public String IQ_ERROR = "error";
    static final public String IQ_BAD_TYPE = "bad_type";

    public Element createIqElement(String type) {
        Element element = createElement("iq");
        element.setAttribute("type", type);
        return element;
    }
}
