/*
 * Copyright 2005-2007 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */
package org.restlet.ext.jaxrs.core;

import java.net.URI;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Variant;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.restlet.ext.jaxrs.todo.NotYetImplementedException;

/**
 * Implementation of the {@link ResponseBuilder}
 * @author Stephan Koops
 * 
 */
public class ResponseBuilderImpl extends ResponseBuilder {

    private ResponseImpl response;

    private MultivaluedMap<String, Object> metadata;

    /**
     * Creates a new Response Builder
     */
    public ResponseBuilderImpl() {
    }

    /**
     * Create a Response instance from the current ResponseBuilder. The builder
     * is reset to a blank state equivalent to calling the ok method.
     * 
     * @return a Response instance
     * @see javax.ws.rs.core.Response.ResponseBuilder#build()
     */
    @Override
    public Response build() {
        if (this.response == null)
            return new ResponseImpl();
        Response r = this.response;
        this.response = null;
        this.metadata = null;
        return r;
    }

    /**
     * Set the cache control data on the ResponseBuilder.
     * 
     * @param cacheControl
     *                the cache control directives
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#cacheControl(javax.ws.rs.core.CacheControl)
     */
    @Override
    public ResponseBuilder cacheControl(CacheControl cacheControl) {
        getMetadata().add(HttpHeaders.CACHE_CONTROL, cacheControl);
        return this;
    }

    /**
     * Set the content location on the ResponseBuilder.
     * 
     * 
     * @param location
     *                the content location
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#contentLocation(java.net.URI)
     */
    @Override
    public ResponseBuilder contentLocation(URI location) {
        getMetadata().putSingle(HttpHeaders.CONTENT_LOCATION,
                location.toASCIIString());
        return this;
    }

    /**
     * Set a new cookie on the ResponseBuilder.
     * 
     * 
     * @param cookie
     *                the new cookie that will accompany the response.
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#cookie(javax.ws.rs.core.NewCookie)
     */
    @Override
    public ResponseBuilder cookie(NewCookie cookie) {
        getMetadata().add(HttpHeaders.SET_COOKIE, cookie);
        return this;
    }

    /**
     * Set the entity on the ResponseBuilder.
     * 
     * 
     * @param entity
     *                the response entity
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#entity(java.lang.Object)
     */
    @Override
    public ResponseBuilder entity(Object entity) {
        this.getResponse().setEntity(entity);
        return this;
    }

    /**
     * Set the value of a specific header on the ResponseBuilder.
     * 
     * @param name
     *                the name of the header
     * @param value
     *                the value of the header, the header will be serialized
     *                using its toString method
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#header(java.lang.String,
     *      java.lang.Object)
     */
    @Override
    public ResponseBuilder header(String name, Object value) {
        if (name == null)
            throw new IllegalArgumentException(
                    "You must give a name of the header");
        getMetadata().add(name, value);
        return this;
        // TODO if cookie, than other date Format, @see Util.formatDate(Date, boolean)
    }

    /**
     * Set the language on the ResponseBuilder.
     * 
     * 
     * @param language
     *                the language of the response entity
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#language(java.lang.String)
     */
    @Override
    public ResponseBuilder language(String language) {
        getMetadata().putSingle(HttpHeaders.CONTENT_LANGUAGE, language);
        return this;
    }

    /**
     * Set the last modified date on the ResponseBuilder.
     * 
     * 
     * @param lastModified
     *                the last modified date
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#lastModified(java.util.Date)
     */
    @Override
    public ResponseBuilder lastModified(Date lastModified) {
        getMetadata().putSingle(HttpHeaders.LAST_MODIFIED, lastModified);
        return this;
    }

    /**
     * Set the location on the ResponseBuilder.
     * 
     * 
     * @param location
     *                the location
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#location(java.net.URI)
     */
    @Override
    public ResponseBuilder location(URI location) {
        getMetadata().putSingle(HttpHeaders.LOCATION, location);
        return this;
    }

    /**
     * Set the status on the ResponseBuilder.
     * 
     * @param status
     *                the response status
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#status(int)
     */
    @Override
    public ResponseBuilder status(int status) {
        if (this.response == null)
            this.response = new ResponseImpl(status);
        else
            this.response.setStatus(status);
        return this;
    }

    /**
     * Set the entity tag on the ResponseBuilder.
     * 
     * 
     * @param tag
     *                the entity tag
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#tag(javax.ws.rs.core.EntityTag)
     */
    @Override
    public ResponseBuilder tag(EntityTag tag) {
        getMetadata().add(HttpHeaders.ETAG, tag);
        return this;
    }

    /**
     * Set the entity tag on the ResponseBuilder.
     * 
     * 
     * @param tag
     *                the entity tag
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#tag(java.lang.String)
     */
    @Override
    public ResponseBuilder tag(String tag) {
        getMetadata().add(HttpHeaders.ETAG, tag);
        return this;
    }

    /**
     * @see javax.ws.rs.core.Response.ResponseBuilder#type(javax.ws.rs.core.MediaType)
     */
    @Override
    public ResponseBuilder type(MediaType type) {
        getMetadata().putSingle(HttpHeaders.CONTENT_TYPE, type);
        return this;
    }

    /**
     * Set the type on the ResponseBuilder.
     * 
     * 
     * @param type
     *                the media type of the response entity
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#type(java.lang.String)
     */
    @Override
    public ResponseBuilder type(String type) {
        getMetadata().putSingle(HttpHeaders.CONTENT_TYPE, type);
        return this;
    }

    /**
     * Set representation metadata on the ResponseBuilder.
     * 
     * 
     * @param variant
     *                metadata of the response entity
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#variant(javax.ws.rs.core.Variant)
     */
    @Override
    public ResponseBuilder variant(Variant variant) {
        MultivaluedMap<String, Object> metadata = getMetadata();
        metadata.putSingle(HttpHeaders.CONTENT_LANGUAGE, variant.getLanguage());
        metadata.putSingle(HttpHeaders.CONTENT_ENCODING, variant.getEncoding());
        metadata.putSingle(HttpHeaders.CONTENT_TYPE, variant.getMediaType());
        return this;
    }

    /**
     * Add a Vary header that lists the available variants.
     * 
     * @param variants
     *                a list of available representation variants
     * @return the updated ResponseBuilder
     * @see javax.ws.rs.core.Response.ResponseBuilder#variants(java.util.List)
     */
    @Override
    public ResponseBuilder variants(List<Variant> variants) {
        // TODO ResponseBuilder.variants(List<Variant> variants)
        throw new NotYetImplementedException();
    }

    ResponseImpl getResponse() {
        if (response == null)
            this.response = new ResponseImpl();
        return response;
    }

    MultivaluedMap<String, Object> getMetadata() {
        if (this.metadata == null)
            this.metadata = getResponse().getMetadata();
        return metadata;
    }
}