//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.server;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.component.Graceful;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * <p>A {@link Connector} accept connections and data from remote peers,
 * and allows applications to send data to remote peers, by setting up
 * the machinery needed to handle such tasks.</p>
 *
 * 连接器
 * 接受远程连接，并且接受数据
 * 它也允许应用发发送数据到远程连接
 */
@ManagedObject("Connector Interface")
public interface Connector extends LifeCycle, Container, Graceful {
    /**
     * 获取服务器
     *
     * @return the {@link Server} instance associated with this {@link Connector}
     */
    public Server getServer();

    /**
     * 获取线程池
     *
     * @return the {@link Executor} used to submit tasks
     */
    public Executor getExecutor();

    /**
     * 获取调度器
     *
     * @return the {@link Scheduler} used to schedule tasks
     */
    public Scheduler getScheduler();

    /**
     * 获取字节缓存池
     *
     * @return the {@link ByteBufferPool} to acquire buffers from and release buffers to
     */
    public ByteBufferPool getByteBufferPool();

    /**
     * 连接工厂
     *
     * @param nextProtocol the next protocol
     * @return the {@link ConnectionFactory} associated with the protocol name
     */
    public ConnectionFactory getConnectionFactory(String nextProtocol);

    /**
     * 获取某一类型的连接工厂
     *
     * @param factoryType
     * @param <T>
     * @return
     */
    public <T> T getConnectionFactory(Class<T> factoryType);
    
    /**
     * 获取默认的连接工厂
     *
     * @return the default {@link ConnectionFactory} associated with the default protocol name
     */
    public ConnectionFactory getDefaultConnectionFactory();

    /**
     * 获取所有的连接工厂
     *
     * @return
     */
    public Collection<ConnectionFactory> getConnectionFactories();

    /**
     * 获取所有支持的协议
     *
     * @return
     */
    public List<String> getProtocols();
    
    /**
     * 获取超时时间
     *
     * @return the max idle timeout for connections in milliseconds
     */
    @ManagedAttribute("maximum time a connection can be idle before being closed (in ms)")
    public long getIdleTimeout();

    /**
     * 获取传输层对象
     *
     * @return the underlying socket, channel, buffer etc. for the connector.
     */
    public Object getTransport();
    
    /**
     * 获取所有的连接
     *
     * @return immutable collection of connected endpoints
     */
    public Collection<EndPoint> getConnectedEndPoints();

    
    /* ------------------------------------------------------------ */
    /**
     * Get the connector name if set.
     * <p>A {@link ContextHandler} may be configured with
     * virtual hosts in the form "@connectorName" and will only serve
     * requests from the named connector.
     *
     * 获取连接器的名称
     *
     * @return The connector name or null.
     */
    public String getName();
}
