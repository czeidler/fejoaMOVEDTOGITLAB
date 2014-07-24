/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.List;


public class InStanzaHandler {
    private String name;
    private boolean isOptionalStanza;
    private boolean stanzaHasBeenHandled = false;

    private InStanzaHandler parent = null;
    final List<InStanzaHandler> childHandlers = new ArrayList<>();

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
        return true;
    }
    public void finished() {

    }

    public void addChildHandler(InStanzaHandler handler) {
        childHandlers.add(handler);
        handler.setParent(this);
    }

    public void setParent(InStanzaHandler parent) {
        this.parent = parent;
    }
}
