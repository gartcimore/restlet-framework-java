/**
 * Copyright 2005-2010 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.engine.nio;

import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectionKey;

import org.restlet.Response;

/**
 * Controls the IO work of parent client helper and manages its connections.
 * 
 * @author Jerome Louvel
 */
public class ClientController extends Controller {

    /**
     * Constructor.
     * 
     * @param helper
     *            The target client helper.
     */
    public ClientController(BaseClientHelper helper) {
        super(helper);
    }

    /**
     * Returns the parent client helper.
     * 
     * @return The parent client helper.
     */
    protected BaseClientHelper getHelper() {
        return (BaseClientHelper) super.getHelper();
    }

    @Override
    protected void handleInbound(Response response) {
        handleInbound(response, getHelper()
                .isSynchronous(response.getRequest()));
    }

    @Override
    protected void handleOutbound(Response response) {
        handleOutbound(response, false);
    }

    @Override
    protected void onSelected(SelectionKey key)
            throws ClosedByInterruptException {

    }

}