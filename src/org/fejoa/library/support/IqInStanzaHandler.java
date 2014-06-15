/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;


import org.xml.sax.Attributes;

public class IqInStanzaHandler extends InStanzaHandler {
    private String type;

    public IqInStanzaHandler(String type) {
        super("iq", false);
        this.type = type;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean handleStanza(Attributes attributes)
    {
        if (attributes.getIndex("type") < 0)
            return false;
        String stanzaType = attributes.getValue("type");
        if (!stanzaType.equals(type))
            return false;
        return true;
    }
}
