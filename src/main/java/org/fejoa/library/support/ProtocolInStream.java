/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class ProtocolInStream extends DefaultHandler {
    private handler_tree root = new handler_tree(null);
    private InStanzaHandler rootHandler;
    private InStanzaHandler currentHandler;
    private handler_tree currentHandlerTree;
    private InputStream xmlInput;

    public ProtocolInStream(byte data[]) {
        currentHandlerTree = root;
        rootHandler = new InStanzaHandler("root", true);
        root.handlers.add(rootHandler);

        xmlInput =  new ByteArrayInputStream(data);
    }

    public void parse() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = null;

        try {
            saxParser = factory.newSAXParser();
            saxParser.parse(new SequenceInputStream(
                    Collections.enumeration(Arrays.asList(
                            new InputStream[]{
                                    new ByteArrayInputStream("<dummy>".getBytes()),
                                    xmlInput,//bogus xml
                                    new ByteArrayInputStream("</dummy>".getBytes()),
                            }))
            ), this);

            //saxParser.parse(xmlInput, this);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(InStanzaHandler handler) {
        rootHandler.addChildHandler(handler);
    }

    private class handler_tree {
        public handler_tree(handler_tree parent) {
            this.parent = parent;
        }

        public handler_tree parent;
        final public List<InStanzaHandler> handlers = new ArrayList<>();
    };

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        currentHandler = null;
        String name = qName;
        if (name.equals("dummy"))
            return;

        handler_tree handlerTree = new handler_tree(currentHandlerTree);
        for (InStanzaHandler handler : currentHandlerTree.handlers) {
            for (InStanzaHandler child : handler.getChild())
                handlerTree.handlers.add(child);
        }
        currentHandlerTree = handlerTree;

        for (InStanzaHandler handler : currentHandlerTree.handlers) {
            if (handler.stanzaName().equals(name)) {
                boolean handled = handler.handleStanza(attributes);
                handler.setHandled(handled);
                if (handled)
                    currentHandler = handler;
            }
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("dummy"))
            return;

        for (InStanzaHandler handler : currentHandlerTree.handlers) {
            if (handler.hasBeenHandled())
                handler.finished();
        }

        handler_tree parent = currentHandlerTree.parent;
        currentHandlerTree = parent;

    }

    public void characters(char ch[], int start, int length) throws SAXException {
        if (currentHandler == null)
            return;
        boolean handled = currentHandler.handleText(new String(ch, start, length));
        currentHandler.setHandled(handled);
    }

};
