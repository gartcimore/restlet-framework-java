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

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.restlet.Connector;
import org.restlet.Response;
import org.restlet.engine.security.SslUtils;

/**
 * A network connection though which messages are exchanged by connectors.
 * Messages can be either requests or responses.
 * 
 * @param <T>
 *            The parent connector type.
 * @author Jerome Louvel
 */
public class Connection<T extends Connector> implements SelectionListener {

    /** The readable selection channel. */
    private ReadableSelectionChannel readableSelectionChannel;

    /** The writable selection channel. */
    private WritableSelectionChannel writableSelectionChannel;

    /** The parent connector helper. */
    private final BaseHelper<T> helper;

    /** The inbound way. */
    private final Way inboundWay;

    /** The timestamp of the last IO activity. */
    private volatile long lastActivity;

    /** The outbound way. */
    private final Way outboundWay;

    /** Indicates if the connection should be persisted across calls. */
    private volatile boolean persistent;

    /** Indicates if idempotent sequences of requests can be pipelined. */
    private volatile boolean pipelining;

    /** The underlying socket channel. */
    private SocketChannel socketChannel;

    /**
     * The socket's NIO selection key holding the link between the channel and
     * the way.
     */
    private volatile SelectionKey socketKey;

    /** The state of the connection. */
    private volatile ConnectionState state;

    /**
     * Constructor.
     * 
     * @param helper
     *            The parent connector helper.
     * @param socketChannel
     *            The underlying NIO socket channel.
     * @throws IOException
     */
    public Connection(BaseHelper<T> helper, SocketChannel socketChannel,
            Selector selector) throws IOException {
        this.helper = helper;
        this.inboundWay = helper.createInboundWay(this);
        this.outboundWay = helper.createOutboundWay(this);
        this.persistent = helper.isPersistingConnections();
        this.pipelining = helper.isPipeliningConnections();
        this.state = ConnectionState.OPENING;
        this.socketChannel = socketChannel;

        if (helper.isTracing()) {
            this.readableSelectionChannel = new ReadableTraceChannel(
                    new ReadableSocketChannel(socketChannel, selector));
            this.writableSelectionChannel = new WritableTraceChannel(
                    new WritableSocketChannel(socketChannel, selector));
        } else {
            this.readableSelectionChannel = new ReadableSocketChannel(
                    socketChannel, selector);
            this.writableSelectionChannel = new WritableSocketChannel(
                    socketChannel, selector);
        }

        this.lastActivity = System.currentTimeMillis();
        this.socketKey = null;
    }

    /**
     * Closes the connection. By default, set the state to
     * {@link ConnectionState#CLOSED}.
     * 
     * @param graceful
     *            Indicates if a graceful close should be attempted.
     */
    public void close(boolean graceful) {
        if (graceful) {
            setState(ConnectionState.CLOSING);
        } else {
            try {
                if (!getSocket().isClosed()) {
                    // Flush the output stream
                    getSocket().getOutputStream().flush();

                    if (!(getSocket() instanceof SSLSocket)) {
                        getSocket().shutdownInput();
                        getSocket().shutdownOutput();
                    }
                }
            } catch (IOException ex) {
                getLogger().log(Level.FINE,
                        "Unable to properly shutdown socket", ex);
            } finally {
                setState(ConnectionState.CLOSED);
            }

            try {
                if (!getSocket().isClosed()) {
                    getSocket().close();
                }
            } catch (IOException ex) {
                getLogger().log(Level.FINE, "Unable to properly close socket",
                        ex);
            } finally {
                setState(ConnectionState.CLOSED);
            }
        }
    }

    /**
     * Asks the server connector to immediately commit the given response
     * associated to this request, making it ready to be sent back to the
     * client. Note that all server connectors don't necessarily support this
     * feature.
     * 
     * @param response
     *            The response to commit.
     */
    public void commit(Response response) {
        getHelper().getOutboundMessages().add(response);
        getHelper().getController().wakeup();
    }

    /**
     * Returns the socket IP address.
     * 
     * @return The socket IP address.
     */
    public String getAddress() {
        return (getSocket().getInetAddress() == null) ? null : getSocket()
                .getInetAddress().getHostAddress();
    }

    /**
     * Returns the parent connector helper.
     * 
     * @return The parent connector helper.
     */
    public BaseHelper<T> getHelper() {
        return helper;
    }

    /**
     * Returns the inbound way.
     * 
     * @return The inbound way.
     */
    public Way getInboundWay() {
        return inboundWay;
    }

    /**
     * Returns the logger.
     * 
     * @return The logger.
     */
    public Logger getLogger() {
        return getHelper().getLogger();
    }

    /**
     * Returns the outbound way.
     * 
     * @return The outbound way.
     */
    public Way getOutboundWay() {
        return outboundWay;
    }

    /**
     * Returns the socket port.
     * 
     * @return The socket port.
     */
    public int getPort() {
        return getSocket().getPort();
    }

    /**
     * Returns the underlying socket channel as a readable selection channel.
     * 
     * @return The underlying socket channel as a readable selection channel.
     */
    protected ReadableSelectionChannel getReadableSelectionChannel() {
        return readableSelectionChannel;
    }

    /**
     * Returns the underlying socket.
     * 
     * @return The underlying socket.
     */
    public Socket getSocket() {
        return (getSocketChannel() == null) ? null : getSocketChannel()
                .socket();
    }

    /**
     * Returns the underlying NIO socket channel.
     * 
     * @return The underlying NIO socket channel.
     */
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    /**
     * Registers interest of this way for socket NIO operations.
     * 
     * @return The operations of interest.
     */
    protected int getSocketInterestOps() {
        return getInboundWay().getSocketInterestOps()
                | getOutboundWay().getSocketInterestOps();
    }

    /**
     * Returns the socket's NIO selection key holding the link between the
     * channel and the way.
     * 
     * @return The socket's NIO selection key holding the link between the
     *         channel and the way.
     */
    protected SelectionKey getSocketKey() {
        return socketKey;
    }

    /**
     * Returns the SSL cipher suite.
     * 
     * @return The SSL cipher suite.
     */
    public String getSslCipherSuite() {
        if (getSocket() instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) getSocket();
            SSLSession sslSession = sslSocket.getSession();

            if (sslSession != null) {
                return sslSession.getCipherSuite();
            }
        }

        return null;
    }

    /**
     * Returns the list of client SSL certificates.
     * 
     * @return The list of client SSL certificates.
     */
    public List<Certificate> getSslClientCertificates() {
        if (getSocket() instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) getSocket();
            SSLSession sslSession = sslSocket.getSession();

            if (sslSession != null) {
                try {
                    List<Certificate> clientCertificates = Arrays
                            .asList(sslSession.getPeerCertificates());
                    return clientCertificates;
                } catch (SSLPeerUnverifiedException e) {
                    getHelper().getLogger().log(Level.FINE,
                            "Can't get the client certificates.", e);
                }
            }
        }

        return null;
    }

    /**
     * Returns the SSL key size, if available and accessible.
     * 
     * @return The SSL key size, if available and accessible.
     */
    public Integer getSslKeySize() {
        Integer keySize = null;
        String sslCipherSuite = getSslCipherSuite();

        if (sslCipherSuite != null) {
            keySize = SslUtils.extractKeySize(sslCipherSuite);
        }

        return keySize;
    }

    /**
     * Returns the state of the connection.
     * 
     * @return The state of the connection.
     */
    public ConnectionState getState() {
        return state;
    }

    /**
     * Returns the underlying socket channel as a writable selection channel.
     * 
     * @return The underlying socket channel as a writable selection channel.
     */
    protected WritableSelectionChannel getWritableSelectionChannel() {
        return writableSelectionChannel;
    }

    /**
     * Indicates if the connection has timed out.
     * 
     * @return True if the connection has timed out.
     */
    public boolean hasTimedOut() {
        return (System.currentTimeMillis() - this.lastActivity) >= getHelper()
                .getMaxIoIdleTimeMs();
    }

    /**
     * Indicates if it is a client-side connection.
     * 
     * @return True if it is a client-side connection.
     */
    public boolean isClientSide() {
        return getHelper().isClientSide();
    }

    /**
     * Indicates if the connection is empty.
     * 
     * @return True if the connection is empty.
     */
    public boolean isEmpty() {
        return getInboundWay().getMessages().isEmpty()
                && getOutboundWay().getMessages().isEmpty();
    }

    /**
     * Indicates if the connection should be persisted across calls.
     * 
     * @return True if the connection should be persisted across calls.
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Indicates if idempotent sequences of requests can be pipelined.
     * 
     * @return True requests pipelining is enabled.
     */
    public boolean isPipelining() {
        return pipelining;
    }

    /**
     * Indicates if it is a server-side connection.
     * 
     * @return True if it is a server-side connection.
     */
    public boolean isServerSide() {
        return getHelper().isServerSide();
    }

    /**
     * Called on error. By default, it calls {@link #close(boolean)} with a
     * 'false' parameter.
     */
    public void onError() {
        close(false);
    }

    /**
     * Callback method invoked when the connection has been selected for IO
     * operations it registered interest in. By default it updates the timestamp
     * that allows the detection of expired connections and calls
     * {@link Way#onSelected()} on the inbound or outbound way.
     * 
     * @param key
     *            The registered selection key.
     */
    public void onSelected(SelectionKey key) {
        this.lastActivity = System.currentTimeMillis();

        try {
            if ((key == null) || key.isReadable()) {
                getInboundWay().onSelected();
            } else if (key.isWritable()) {
                getOutboundWay().onSelected();
            } else if (key.isConnectable()) {
                // Client-side asynchronous connection
                try {
                    if (getSocketChannel().finishConnect()) {
                        open();
                    } else {
                        getLogger().info(
                                "Unable to establish a connection to "
                                        + getSocket().getInetAddress());
                        setState(ConnectionState.CLOSING);
                    }
                } catch (IOException e) {
                    getLogger().warning(
                            "Unable to establish a connection to "
                                    + getSocket().getInetAddress());
                    setState(ConnectionState.CLOSING);
                }
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING,
                    "Unexpected error detected. Closing the connection.", t);
            onError();
        }
    }

    /**
     * Opens the connection. By default, set the IO state of the connection to
     * {@link ConnectionState#OPEN} and the IO state of the inbound way to
     * {@link IoState#INTEREST}.
     */
    public void open() {
        setState(ConnectionState.OPEN);
        updateState();
    }

    /**
     * Recycles the connection so it can be reused. Typically invoked by a
     * connection pool.
     */
    public void recycle() {
        this.readableSelectionChannel = null;
        this.socketChannel = null;
        this.socketKey = null;
        this.state = null;
        this.writableSelectionChannel = null;
        this.inboundWay.recycle();
        this.outboundWay.recycle();
    }

    /**
     * Registers interest of this connection for NIO operations with the given
     * selector. If called several times, it just update the selection keys with
     * the new interest operations.
     * 
     * @param selector
     *            The selector to register with.
     * @throws ClosedChannelException
     */
    public void registerInterest(Selector selector) {
        // Give a chance to ways for addition registrations
        getInboundWay().registerInterest(selector);
        getOutboundWay().registerInterest(selector);

        // Get the socket interest
        int socketInterestOps = getSocketInterestOps();

        if (socketInterestOps > 0) {
            // IO interest declared
            if (getSocketKey() == null) {
                // Create a new selection key
                try {
                    setSocketKey(getSocketChannel().register(selector,
                            socketInterestOps, this));
                } catch (ClosedChannelException cce) {
                    getLogger()
                            .log(Level.WARNING,
                                    "Unable to register NIO interest operations for this connection",
                                    cce);
                    onError();
                }
            } else {
                // Update the existing selection key
                getSocketKey().interestOps(socketInterestOps);
            }
        } else {
            // No IO interest declared
            if (getSocketKey() != null) {
                // Free the existing selection key
                getSocketKey().cancel();
                getSocketKey().attach(null);
                setSocketKey(null);
            }
        }
    }

    /**
     * Indicates if the connection should be persisted across calls.
     * 
     * @param persistent
     *            True if the connection should be persisted across calls.
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Indicates if idempotent sequences of requests can be pipelined.
     * 
     * @param pipelining
     *            True requests pipelining is enabled.
     */
    public void setPipelining(boolean pipelining) {
        this.pipelining = pipelining;
    }

    /**
     * Sets the socket's NIO selection key holding the link between the channel
     * and the way.
     * 
     * @param socketKey
     *            The socket's NIO selection key holding the link between the
     *            channel and the way.
     */
    protected void setSocketKey(SelectionKey socketKey) {
        this.socketKey = socketKey;
    }

    /**
     * Sets the state of the connection.
     * 
     * @param state
     *            The state of the connection.
     */
    public void setState(ConnectionState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return getState() + ", " + getInboundWay() + ", " + getOutboundWay();
    }

    /**
     * Updates the connection states.
     */
    public void updateState() {
        getInboundWay().updateState();
        getOutboundWay().updateState();
    }

}
