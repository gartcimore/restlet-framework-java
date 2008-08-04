/**
 * Copyright 2005-2008 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of the following open
 * source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 (the "Licenses"). You can
 * select the license that you prefer but you may not use this file except in
 * compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.gnu.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.sun.com/cddl/cddl.html
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royaltee free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine/.
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */
package org.restlet.ext.jaxrs.internal.exceptions;

import javax.ws.rs.Path;

/**
 * This kind of exception is thrown, when an &#64{@link Path} annotation of a
 * method contains illegal charactres.
 * 
 * @author Stephan Koops
 */
public class IllegalPathOnMethodException extends IllegalPathException {

    private static final long serialVersionUID = -6655373875338074948L;

    /**
     * @param ipe
     */
    public IllegalPathOnMethodException(IllegalPathException ipe) {
        super(ipe.getPath(), ipe.getMessage());
        setStackTrace(ipe.getStackTrace());
    }
}
