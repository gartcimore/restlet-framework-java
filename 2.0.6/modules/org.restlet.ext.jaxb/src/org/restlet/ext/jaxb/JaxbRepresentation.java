/**
 * Copyright 2005-2011 Noelios Technologies.
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

package org.restlet.ext.jaxb;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.util.JAXBSource;
import javax.xml.transform.sax.SAXSource;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.ext.jaxb.internal.Marshaller;
import org.restlet.ext.jaxb.internal.Unmarshaller;
import org.restlet.representation.Representation;
import org.restlet.representation.WriterRepresentation;
import org.xml.sax.InputSource;

/**
 * An XML representation based on JAXB that provides easy translation between
 * XML and JAXB element class trees.
 * 
 * @author Overstock.com
 * @author Jerome Louvel
 * @param <T>
 *            The type to wrap.
 */
public class JaxbRepresentation<T> extends WriterRepresentation {

    /** Improves performance by caching contexts which are expensive to create. */
    private final static Map<String, JAXBContext> contexts = new TreeMap<String, JAXBContext>();

    /**
     * Returns the JAXB context, if possible from the cached contexts.
     * 
     * @param contextPath
     * 
     * @param classLoader
     * 
     * @return The JAXB context.
     * @throws JAXBException
     */
    public static synchronized JAXBContext getContext(String contextPath,
            ClassLoader classLoader) throws JAXBException {
        // Contexts are thread-safe so reuse those.
        JAXBContext result = contexts.get(contextPath);

        if (result == null) {
            result = classLoader == null ? JAXBContext.newInstance(contextPath)
                    : JAXBContext.newInstance(contextPath, classLoader);
            contexts.put(contextPath, result);
        }

        return result;
    }

    /**
     * The list of Java package names that contain schema derived class and/or
     * Java to schema (JAXB-annotated) mapped classes.
     */
    private volatile String contextPath;

    /**
     * The classloader to use for JAXB annotated classes.
     */
    private volatile ClassLoader classLoader;

    /**
     * Indicates if the resulting XML data should be formatted with line breaks
     * and indentation. Defaults to false.
     */
    private volatile boolean formattedOutput;

    /**
     * Indicates whether or not document level events will be generated by the
     * Marshaller.
     */
    private volatile boolean fragment;

    /** The "xsi:noNamespaceSchemaLocation" attribute in the generated XML data. */
    private volatile String noNamespaceSchemaLocation;

    /** The wrapped Java object. */
    private volatile T object;

    /** The "xsi:schemaLocation" attribute in the generated XML data */
    private volatile String schemaLocation;

    /** The JAXB validation event handler. */
    private volatile ValidationEventHandler validationEventHandler;

    /** The source XML representation. */
    private volatile Representation xmlRepresentation;

    /**
     * Creates a JAXB representation from an existing JAXB content tree.
     * 
     * @param mediaType
     *            The representation's media type.
     * @param object
     *            The Java object.
     */
    public JaxbRepresentation(MediaType mediaType, T object) {
        this(mediaType, object, (object != null) ? object.getClass()
                .getClassLoader() : null);
    }

    /**
     * Creates a JAXB representation from an existing JAXB content tree.
     * 
     * @param mediaType
     *            The representation's media type.
     * @param object
     *            The Java object.
     * @param classloader
     *            The classloader to use for JAXB annotated classes.
     */
    private JaxbRepresentation(MediaType mediaType, T object,
            ClassLoader classloader) {
        super(mediaType);
        this.classLoader = classloader;
        this.contextPath = (object != null) ? object.getClass().getPackage()
                .getName() : null;
        this.object = object;
        this.validationEventHandler = null;
        this.xmlRepresentation = null;
    }

    /**
     * Creates a new JAXB representation, converting the input XML into a Java
     * content tree. The XML is validated.
     * 
     * @param xmlRepresentation
     *            The XML wrapped in a representation.
     * @param type
     *            The type to convert to.
     * 
     * @throws JAXBException
     *             If the incoming XML does not validate against the schema.
     * @throws IOException
     *             If unmarshalling XML fails.
     */
    public JaxbRepresentation(Representation xmlRepresentation, Class<T> type) {
        this(xmlRepresentation, type.getPackage().getName(), null, type
                .getClassLoader());
    }

    /**
     * Creates a new JAXB representation, converting the input XML into a Java
     * content tree. The XML is validated.
     * 
     * @param xmlRepresentation
     *            The XML wrapped in a representation.
     * @param type
     *            The type to convert to.
     * @param validationHandler
     *            A handler for dealing with validation failures.
     * 
     * @throws JAXBException
     *             If the incoming XML does not validate against the schema.
     * @throws IOException
     *             If unmarshalling XML fails.
     */
    public JaxbRepresentation(Representation xmlRepresentation, Class<T> type,
            ValidationEventHandler validationHandler) {
        this(xmlRepresentation, type.getPackage().getName(), validationHandler,
                type.getClassLoader());
    }

    /**
     * Creates a new JAXB representation, converting the input XML into a Java
     * content tree. The XML is validated.
     * 
     * @param xmlRepresentation
     *            The XML wrapped in a representation.
     * @param contextPath
     *            The list of Java package names for JAXB.
     * 
     * @throws JAXBException
     *             If the incoming XML does not validate against the schema.
     * @throws IOException
     *             If unmarshalling XML fails.
     */
    public JaxbRepresentation(Representation xmlRepresentation,
            String contextPath) {
        this(xmlRepresentation, contextPath, null, null);
    }

    /**
     * Creates a new JAXB representation, converting the input XML into a Java
     * content tree. The XML is validated.
     * 
     * @param xmlRepresentation
     *            The XML wrapped in a representation.
     * @param contextPath
     *            The list of Java package names for JAXB.
     * @param validationHandler
     *            A handler for dealing with validation failures.
     * 
     * @throws JAXBException
     *             If the incoming XML does not validate against the schema.
     * @throws IOException
     *             If unmarshalling XML fails.
     */
    public JaxbRepresentation(Representation xmlRepresentation,
            String contextPath, ValidationEventHandler validationHandler) {
        this(xmlRepresentation, contextPath, validationHandler, null);
    }

    /**
     * Creates a new JAXB representation, converting the input XML into a Java
     * content tree. The XML is validated.
     * 
     * @param xmlRepresentation
     *            The XML wrapped in a representation.
     * @param contextPath
     *            The list of Java package names for JAXB.
     * @param validationHandler
     *            A handler for dealing with validation failures.
     * @param classLoader
     *            The classloader to use for JAXB annotated classes.
     * @throws JAXBException
     *             If the incoming XML does not validate against the schema.
     * @throws IOException
     *             If unmarshalling XML fails.
     */
    private JaxbRepresentation(Representation xmlRepresentation,
            String contextPath, ValidationEventHandler validationHandler,
            ClassLoader classLoader) {
        super(xmlRepresentation.getMediaType());
        this.classLoader = classLoader;
        this.contextPath = contextPath;
        this.object = null;
        this.validationEventHandler = validationHandler;
        this.xmlRepresentation = xmlRepresentation;

    }

    /**
     * Creates a JAXB representation from an existing JAXB content tree with
     * {@link MediaType#APPLICATION_XML}.
     * 
     * @param object
     *            The Java object.
     */
    public JaxbRepresentation(T object) {
        this(MediaType.APPLICATION_XML, object);
    }

    /**
     * Returns the classloader to use for JAXB annotated classes.
     * 
     * @return The classloader to use for JAXB annotated classes.
     */
    private ClassLoader getClassLoader() {
        return this.classLoader;
    }

    /**
     * Returns the JAXB context.
     * 
     * @return The JAXB context.
     * @throws JAXBException
     */
    public JAXBContext getContext() throws JAXBException {
        return getContext(getContextPath(), getClassLoader());
    }

    /**
     * Returns the list of Java package names that contain schema derived class
     * and/or Java to schema (JAXB-annotated) mapped classes
     * 
     * @return The list of Java package names.
     */
    public String getContextPath() {
        return this.contextPath;
    }

    /**
     * Returns the XML representation as a SAX input source.
     * 
     * @return The SAX input source.
     * @deprecated
     */
    @Deprecated
    public InputSource getInputSource() throws IOException {
        return new InputSource(this.xmlRepresentation.getStream());
    }

    /**
     * Returns a JAXB SAX source.
     * 
     * @return A JAXB SAX source.
     */
    public JAXBSource getJaxbSource() throws IOException {
        try {
            return new JAXBSource(getContext(), getObject());
        } catch (JAXBException e) {
            throw new IOException(
                    "JAXBException while creating the JAXBSource: "
                            + e.getMessage());
        }
    }

    /**
     * Returns the "xsi:noNamespaceSchemaLocation" attribute in the generated
     * XML data.
     * 
     * @return The "xsi:noNamespaceSchemaLocation" attribute in the generated
     *         XML data.
     */
    public String getNoNamespaceSchemaLocation() {
        return noNamespaceSchemaLocation;
    }

    /**
     * Returns the wrapped Java object.
     * 
     * @return The wrapped Java object.
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public T getObject() throws IOException {
        if ((this.object == null) && (this.xmlRepresentation != null)) {
            // Try to unmarshal the wrapped XML representation
            final Unmarshaller<T> u = new Unmarshaller<T>(this.contextPath,
                    this.classLoader);
            if (getValidationEventHandler() != null) {
                try {
                    u.setEventHandler(getValidationEventHandler());
                } catch (JAXBException e) {
                    Context.getCurrentLogger().log(Level.WARNING,
                            "Unable to set the event handler", e);
                    throw new IOException("Unable to set the event handler."
                            + e.getMessage());
                }
            }

            try {
                this.object = (T) u.unmarshal(this.xmlRepresentation
                        .getReader());
            } catch (JAXBException e) {
                Context.getCurrentLogger().log(Level.WARNING,
                        "Unable to unmarshal the XML representation", e);
                throw new IOException(
                        "Unable to unmarshal the XML representation."
                                + e.getMessage());
            }
        }
        return this.object;
    }

    /**
     * Returns a JAXB SAX source.
     * 
     * @return A JAXB SAX source.
     * @deprecated Use {@link #getJaxbSource()} instead.
     */
    @Deprecated
    public SAXSource getSaxSource() throws IOException {
        return getJaxbSource();
    }

    /**
     * Returns the "xsi:schemaLocation" attribute in the generated XML data.
     * 
     * @return The "xsi:schemaLocation" attribute in the generated XML data.
     */
    public String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * Returns the optional validation event handler.
     * 
     * @return The optional validation event handler.
     */
    public ValidationEventHandler getValidationEventHandler() {
        return this.validationEventHandler;
    }

    /**
     * Indicates if the resulting XML data should be formatted with line breaks
     * and indentation. Defaults to false.
     * 
     * @return the formattedOutput
     */
    public boolean isFormattedOutput() {
        return this.formattedOutput;
    }

    /**
     * Indicates whether or not document level events will be generated by the
     * Marshaller.
     * 
     * @return True if the document level events will be generated by the
     *         Marshaller.
     */
    public boolean isFragment() {
        return fragment;
    }

    /**
     * Sets the list of Java package names that contain schema derived class
     * and/or Java to schema (JAXB-annotated) mapped classes.
     * 
     * @param contextPath
     *            The JAXB context path.
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Indicates if the resulting XML data should be formatted with line breaks
     * and indentation.
     * 
     * @param formattedOutput
     *            True if the resulting XML data should be formatted.
     */
    public void setFormattedOutput(boolean formattedOutput) {
        this.formattedOutput = formattedOutput;
    }

    /**
     * Indicates whether or not document level events will be generated by the
     * Marshaller.
     * 
     * @param fragment
     *            True if the document level events will be generated by the
     *            Marshaller.
     */
    public void setFragment(boolean fragment) {
        this.fragment = fragment;
    }

    /**
     * Sets the "xsi:noNamespaceSchemaLocation" attribute in the generated XML
     * data.
     * 
     * @param noNamespaceSchemaLocation
     *            The "xsi:noNamespaceSchemaLocation" attribute in the generated
     *            XML data.
     */
    public void setNoNamespaceSchemaLocation(String noNamespaceSchemaLocation) {
        this.noNamespaceSchemaLocation = noNamespaceSchemaLocation;
    }

    /**
     * Sets the wrapped Java object.
     * 
     * @param object
     *            The Java object to set.
     */
    public void setObject(T object) {
        this.object = object;
    }

    /**
     * Sets the "xsi:schemaLocation" attribute in the generated XML data.
     * 
     * @param schemaLocation
     *            The "xsi:noNamespaceSchemaLocation" attribute in the generated
     *            XML data.
     */
    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    /**
     * Sets the validation event handler.
     * 
     * @param validationEventHandler
     *            The optional validation event handler.
     */
    public void setValidationEventHandler(
            ValidationEventHandler validationEventHandler) {
        this.validationEventHandler = validationEventHandler;
    }

    /**
     * Writes the representation to a stream of characters.
     * 
     * @param writer
     *            The writer to use when writing.
     * 
     * @throws IOException
     *             If any error occurs attempting to write the stream.
     */
    @Override
    public void write(Writer writer) throws IOException {
        try {
            new Marshaller<T>(this, this.contextPath, getClassLoader())
                    .marshal(getObject(), writer);
        } catch (JAXBException e) {
            Context.getCurrentLogger().log(Level.WARNING,
                    "JAXB marshalling error caught.", e);

            // Maybe the tree represents a failure, try that.
            try {
                new Marshaller<T>(this, "failure", getClassLoader()).marshal(
                        getObject(), writer);
            } catch (JAXBException e2) {
                // We don't know what package this tree is from.
                throw new IOException(e.getMessage());
            }
        }
    }

}
