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
import java.util.List;


class InStanzaHandler {
    private String name;
    private boolean isOptionalStanza;
    private boolean stanzaHasBeenHandled = false;

    private InStanzaHandler parent = null;
    List<InStanzaHandler> childHandlers;

    public InStanzaHandler(String stanza, boolean optional) {
        name = stanza;
        isOptionalStanza = optional;
    }

    public void finish() {
        for (InStanzaHandler handler : childHandlers)
            handler.finish();
        childHandlers.clear();
    }

    public String stanzaName() {
        return name;
    }
    public boolean isOptional() {
        return isOptionalStanza;
    }
    public boolean hasBeenHandled() {
        if (!stanzaHasBeenHandled)
            return false;
        for (InStanzaHandler child : childHandlers) {
            if (!child.isOptional() && !child.hasBeenHandled())
                return false;
        }
        return true;
    }

    public void setHandled(boolean handled) {
        stanzaHasBeenHandled = handled;
    }
    public InStanzaHandler getParent() {
        return parent;
    }
    public List<InStanzaHandler> getChild() {
        return childHandlers;
    }

    public boolean handleStanza(Attributes attributes) {
        return false;
    }
    public boolean handleText(String text) {
        return false;
    }
    public void finished() {

    }

    void addChildHandler(InStanzaHandler handler) {
        childHandlers.add(handler);
        handler.setParent(this);
    }

    void setParent(InStanzaHandler parent) {
        this.parent = parent;
    }
};


class ProtocolInStream extends DefaultHandler {
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
            saxParser.parse(xmlInput, this);
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
        public List<InStanzaHandler> handlers;
    };

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        currentHandler = null;
        String name = qName;

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
