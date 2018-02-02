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

package org.eclipse.jetty.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * 网络通道终端
 */
public class SocketChannelEndPoint extends ChannelEndPoint {

    /**
     * 日志类
     */
    private static final Logger LOG = Log.getLogger(SocketChannelEndPoint.class);

    /**
     * socket类
     */
    private final Socket _socket;

    /**
     * 本地地址
     * 即服务器地址
     */
    private final InetSocketAddress _local;

    /**
     * 远程地址
     * 即客户端地址
     */
    private final InetSocketAddress _remote;

    /**
     * 构造方法
     *
     * @param channel
     * @param selector
     * @param key
     * @param scheduler
     */
    public SocketChannelEndPoint(SelectableChannel channel, ManagedSelector selector, SelectionKey key, Scheduler scheduler) {
        this((SocketChannel)channel,selector,key,scheduler);
    }

    /**
     * 构造方法
     *
     * @param channel
     * @param selector
     * @param key
     * @param scheduler
     */
    public SocketChannelEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey key, Scheduler scheduler) {
        super(channel,selector,key,scheduler);
        _socket=channel.socket();
        _local=(InetSocketAddress)_socket.getLocalSocketAddress();
        _remote=(InetSocketAddress)_socket.getRemoteSocketAddress();
    }

    /**
     * 获取socket
     *
     * @return
     */
    public Socket getSocket() {
        return _socket;
    }

    /**
     * 获取本地地址
     *
     * @return
     */
    public InetSocketAddress getLocalAddress() {
        return _local;
    }

    /**
     * 获取远程地址
     *
     * @return
     */
    public InetSocketAddress getRemoteAddress() {
        return _remote;
    }

    /**
     * 关闭输出流
     */
    @Override
    protected void doShutdownOutput() {
        try {
            if (!_socket.isOutputShutdown())
                _socket.shutdownOutput();
        } catch (IOException e) {
            LOG.debug(e);
        }
    }
}
