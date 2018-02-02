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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.ArrayUtil;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * <p>Provides the common handling for {@link ConnectionFactory} implementations including:</p>
 * <ul>
 * <li>Protocol identification</li>
 * <li>Configuration of new Connections:
 *     <ul>
 *     <li>Setting inputbuffer size</li>
 *     <li>Calling {@link Connection#addListener(Connection.Listener)} for all
 *     Connection.Listener instances found as beans on the {@link Connector}
 *     and this {@link ConnectionFactory}</li>
 *     </ul>
 * </ul>
 *
 * 抽象的连接工厂
 * 它包含的内容有:
 * 1.设置输入缓存的大小
 * 2.添加监听器
 */
@ManagedObject
public abstract class AbstractConnectionFactory extends ContainerLifeCycle implements ConnectionFactory {

    /**
     * 协议
     */
    private final String _protocol;

    /**
     * 协议列表
     */
    private final List<String> _protocols;

    /**
     * 输入缓存
     */
    private int _inputbufferSize = 8192;

    /**
     * 构造方法
     *
     * @param protocol
     */
    protected AbstractConnectionFactory(String protocol) {
        _protocol = protocol;
        _protocols = Collections.unmodifiableList(Arrays.asList(new String[]{protocol}));
    }

    /**
     * 构造方法
     *
     * @param protocols
     */
    protected AbstractConnectionFactory(String... protocols) {
        _protocol = protocols[0];
        _protocols = Collections.unmodifiableList(Arrays.asList(protocols));
    }

    /**
     * 获取协议
     *
     * @return
     */
    @Override
    @ManagedAttribute(value = "The protocol name", readonly = true)
    public String getProtocol() {
        return _protocol;
    }

    /**
     * 获取协议列表
     *
     * @return
     */
    @Override
    public List<String> getProtocols() {
        return _protocols;
    }

    /**
     * 获取输入缓冲大小
     *
     * @return
     */
    @ManagedAttribute("The buffer size used to read from the network")
    public int getInputBufferSize() {
        return _inputbufferSize;
    }

    /**
     * 设置输入缓冲大小
     *
     * @param size
     */
    public void setInputBufferSize(int size) {
        _inputbufferSize=size;
    }

    /**
     * 配置
     *
     * @param connection
     * @param connector
     * @param endPoint
     * @return
     */
    protected AbstractConnection configure(AbstractConnection connection, Connector connector, EndPoint endPoint) {
        connection.setInputBufferSize(getInputBufferSize());

        // Add Connection.Listeners from Connector
        // 它会从connector中获取监听器，并且添加给连接
        if (connector instanceof ContainerLifeCycle) {
            ContainerLifeCycle aggregate = (ContainerLifeCycle)connector;
            for (Connection.Listener listener : aggregate.getBeans(Connection.Listener.class)) {
                connection.addListener(listener);
            }
        }

        // Add Connection.Listeners from this factory
        // 从工厂中添加监听器
        for (Connection.Listener listener : getBeans(Connection.Listener.class)) {
            connection.addListener(listener);
        }

        return connection;
    }

    /**
     * 转换为字符串
     *
     * @return
     */
    @Override
    public String toString() {
        return String.format("%s@%x%s",this.getClass().getSimpleName(),hashCode(),getProtocols());
    }

    /**
     * 获取工厂
     *
     * @param sslContextFactory
     * @param factories
     * @return
     */
    public static ConnectionFactory[] getFactories(SslContextFactory sslContextFactory, ConnectionFactory... factories) {
        factories=ArrayUtil.removeNulls(factories);

        if (sslContextFactory==null) {
            return factories;
        }

        for (ConnectionFactory factory : factories) {
            if (factory instanceof HttpConfiguration.ConnectionFactory) {
                HttpConfiguration config = ((HttpConfiguration.ConnectionFactory)factory).getHttpConfiguration();
                if (config.getCustomizer(SecureRequestCustomizer.class)==null) {
                    config.addCustomizer(new SecureRequestCustomizer());
                }
            }
        }
        return ArrayUtil.prependToArray(new SslConnectionFactory(sslContextFactory,factories[0].getProtocol()),factories,ConnectionFactory.class);

    }
}
