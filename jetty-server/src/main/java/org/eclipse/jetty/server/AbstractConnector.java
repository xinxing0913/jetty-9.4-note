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

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Locker;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * <p>An abstract implementation of {@link Connector} that provides a {@link ConnectionFactory} mechanism
 * for creating {@link org.eclipse.jetty.io.Connection} instances for various protocols (HTTP, SSL, etc).</p>
 *
 * <h2>Connector Services</h2>
 * The abstract connector manages the dependent services needed by all specific connector instances:
 * <ul>
 * <li>The {@link Executor} service is used to run all active tasks needed by this connector such as accepting connections
 * or handle HTTP requests. The default is to use the {@link Server#getThreadPool()} as an executor.
 * </li>
 * <li>The {@link Scheduler} service is used to monitor the idle timeouts of all connections and is also made available
 * to the connections to time such things as asynchronous request timeouts.  The default is to use a new
 * {@link ScheduledExecutorScheduler} instance.
 * </li>
 * <li>The {@link ByteBufferPool} service is made available to all connections to be used to acquire and release
 * {@link ByteBuffer} instances from a pool.  The default is to use a new {@link ArrayByteBufferPool} instance.
 * </li>
 * </ul>
 * These services are managed as aggregate beans by the {@link ContainerLifeCycle} super class and
 * may either be managed or unmanaged beans.
 *
 * <h2>Connection Factories</h2>
 * The connector keeps a collection of {@link ConnectionFactory} instances, each of which are known by their
 * protocol name.  The protocol name may be a real protocol (e.g. "http/1.1" or "h2") or it may be a private name
 * that represents a special connection factory. For example, the name "SSL-http/1.1" is used for
 * an {@link SslConnectionFactory} that has been instantiated with the {@link HttpConnectionFactory} as it's
 * next protocol.
 *
 * <h2>Configuring Connection Factories</h2>
 * The collection of available {@link ConnectionFactory} may be constructor injected or modified with the
 * methods {@link #addConnectionFactory(ConnectionFactory)}, {@link #removeConnectionFactory(String)} and
 * {@link #setConnectionFactories(Collection)}.  Only a single {@link ConnectionFactory} instance may be configured
 * per protocol name, so if two factories with the same {@link ConnectionFactory#getProtocol()} are set, then
 * the second will replace the first.
 * <p>
 * The protocol factory used for newly accepted connections is specified by
 * the method {@link #setDefaultProtocol(String)} or defaults to the protocol of the first configured factory.
 * <p>
 * Each Connection factory type is responsible for the configuration of the protocols that it accepts. Thus to
 * configure the HTTP protocol, you pass a {@link HttpConfiguration} instance to the {@link HttpConnectionFactory}
 * (or other factories that can also provide HTTP Semantics).  Similarly the {@link SslConnectionFactory} is
 * configured by passing it a {@link SslContextFactory} and a next protocol name.
 *
 * <h2>Connection Factory Operation</h2>
 * {@link ConnectionFactory}s may simply create a {@link org.eclipse.jetty.io.Connection} instance to support a specific
 * protocol.  For example, the {@link HttpConnectionFactory} will create a {@link HttpConnection} instance
 * that can handle http/1.1, http/1.0 and http/0.9.
 * <p>
 * {@link ConnectionFactory}s may also create a chain of {@link org.eclipse.jetty.io.Connection} instances, using other {@link ConnectionFactory} instances.
 * For example, the {@link SslConnectionFactory} is configured with a next protocol name, so that once it has accepted
 * a connection and created an {@link SslConnection}, it then used the next {@link ConnectionFactory} from the
 * connector using the {@link #getConnectionFactory(String)} method, to create a {@link org.eclipse.jetty.io.Connection} instance that
 * will handle the unencrypted bytes from the {@link SslConnection}.   If the next protocol is "http/1.1", then the
 * {@link SslConnectionFactory} will have a protocol name of "SSL-http/1.1" and lookup "http/1.1" for the protocol
 * to run over the SSL connection.
 * <p>
 * {@link ConnectionFactory}s may also create temporary {@link org.eclipse.jetty.io.Connection} instances that will exchange bytes
 * over the connection to determine what is the next protocol to use.  For example the ALPN protocol is an extension
 * of SSL to allow a protocol to be specified during the SSL handshake. ALPN is used by the HTTP/2 protocol to
 * negotiate the protocol that the client and server will speak.  Thus to accept a HTTP/2 connection, the
 * connector will be configured with {@link ConnectionFactory}s for "SSL-ALPN", "h2", "http/1.1"
 * with the default protocol being "SSL-ALPN".  Thus a newly accepted connection uses "SSL-ALPN", which specifies a
 * SSLConnectionFactory with "ALPN" as the next protocol.  Thus an SSL connection instance is created chained to an ALPN
 * connection instance.  The ALPN connection then negotiates with the client to determined the next protocol, which
 * could be "h2" or the default of "http/1.1".  Once the next protocol is determined, the ALPN connection
 * calls {@link #getConnectionFactory(String)} to create a connection instance that will replace the ALPN connection as
 * the connection chained to the SSL connection.
 * <h2>Acceptors</h2>
 * The connector will execute a number of acceptor tasks to the {@link Exception} service passed to the constructor.
 * The acceptor tasks run in a loop while the connector is running and repeatedly call the abstract {@link #accept(int)} method.
 * The implementation of the accept method must:
 * <ol>
 * <li>block waiting for new connections</li>
 * <li>accept the connection (eg socket accept)</li>
 * <li>perform any configuration of the connection (eg. socket linger times)</li>
 * <li>call the {@link #getDefaultConnectionFactory()} {@link ConnectionFactory#newConnection(Connector, org.eclipse.jetty.io.EndPoint)}
 * method to create a new Connection instance.</li>
 * </ol>
 * The default number of acceptor tasks is the minimum of 1 and the number of available CPUs divided by 8. Having more acceptors may reduce
 * the latency for servers that see a high rate of new connections (eg HTTP/1.0 without keep-alive).  Typically the default is
 * sufficient for modern persistent protocols (HTTP/1.1, HTTP/2 etc.)
 *
 * 抽象连接器
 */
@ManagedObject("Abstract implementation of the Connector Interface")
public abstract class AbstractConnector extends ContainerLifeCycle implements Connector, Dumpable {
    /**
     * 日志类
     */
    protected final Logger LOG = Log.getLogger(AbstractConnector.class);

    /**
     * 锁
     */
    private final Locker _locker = new Locker();

    /**
     * 在接受的时候的锁
     */
    private final Condition _setAccepting = _locker.newCondition();

    /**
     * Order is important on server side, so we use a LinkedHashMap
     *
     * 顺序很重要，因此我们这里使用LinkedHashMap
     */
    private final Map<String, ConnectionFactory> _factories = new LinkedHashMap<>();

    /**
     * 服务类
     */
    private final Server _server;

    /**
     * 线程池
     */
    private final Executor _executor;

    /**
     * 调度器
     */
    private final Scheduler _scheduler;

    /**
     * 字节缓存池
     */
    private final ByteBufferPool _byteBufferPool;

    /**
     * 接受的线程数组
     */
    private final Thread[] _acceptors;

    /**
     * 终端列表
     */
    private final Set<EndPoint> _endpoints = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 不可变的终端列表
     */
    private final Set<EndPoint> _immutableEndPoints = Collections.unmodifiableSet(_endpoints);

    /**
     * 计数同步工具
     */
    private CountDownLatch _stopping;

    /**
     * 最大空闲时间
     */
    private long _idleTimeout = 30000;

    /**
     * 默认协议
     */
    private String _defaultProtocol;

    /**
     * 连接工厂
     */
    private ConnectionFactory _defaultConnectionFactory;

    /**
     * 名称
     */
    private String _name;

    /**
     * 优先级
     */
    private int _acceptorPriorityDelta = -2;

    /**
     * 是否还接受连接
     */
    private boolean _accepting = true;

    /**
     * 构造方法
     *
     * @param server The server this connector will be added to. Must not be null.
     * @param executor An executor for this connector or null to use the servers executor
     * @param scheduler A scheduler for this connector or null to either a {@link Scheduler} set as a server bean or if none set, then a new {@link ScheduledExecutorScheduler} instance.
     * @param pool A buffer pool for this connector or null to either a {@link ByteBufferPool} set as a server bean or none set, the new  {@link ArrayByteBufferPool} instance.
     * @param acceptors the number of acceptor threads to use, or -1 for a default value. If 0, then no acceptor threads will be launched and some other mechanism will need to be used to accept new connections.
     * @param factories The Connection Factories to use.
     */
    public AbstractConnector(
            Server server,
            Executor executor,
            Scheduler scheduler,
            ByteBufferPool pool,
            int acceptors,
            ConnectionFactory... factories) {
        _server = server;
        _executor = executor != null ? executor : _server.getThreadPool();
        if (scheduler == null) {
            scheduler = _server.getBean(Scheduler.class);
        }
        _scheduler = scheduler != null ? scheduler : new ScheduledExecutorScheduler();
        if (pool == null) {
            pool=_server.getBean(ByteBufferPool.class);
        }
        _byteBufferPool = pool != null ? pool : new ArrayByteBufferPool();

        addBean(_server,false);
        addBean(_executor);
        if (executor==null) {
            unmanage(_executor); // inherited from server
        }
        addBean(_scheduler);
        addBean(_byteBufferPool);

        for (ConnectionFactory factory:factories) {
            addConnectionFactory(factory);
        }

        // 可用的处理器个数
        int cores = Runtime.getRuntime().availableProcessors();
        if (acceptors < 0) {
            acceptors = Math.max(1, Math.min(4, cores/8));
        }
        if (acceptors > cores) {
            LOG.warn("Acceptors should be <= availableProcessors: " + this);
        }
        _acceptors = new Thread[acceptors];
    }

    /**
     * 获取服务器信息
     *
     * @return
     */
    @Override
    public Server getServer() {
        return _server;
    }

    /**
     * 获取线程池
     */
    @Override
    public Executor getExecutor() {
        return _executor;
    }

    /**
     * 获取字节缓存池
     *
     * @return
     */
    @Override
    public ByteBufferPool getByteBufferPool() {
        return _byteBufferPool;
    }

    /**
     * 获取空闲超时时间
     *
     * @return
     */
    @Override
    @ManagedAttribute("Idle timeout")
    public long getIdleTimeout() {
        return _idleTimeout;
    }
    
    /**
     * <p>Sets the maximum Idle time for a connection, which roughly translates to the {@link Socket#setSoTimeout(int)}
     * call, although with NIO implementations other mechanisms may be used to implement the timeout.</p>
     * <p>The max idle time is applied:</p>
     * <ul>
     * <li>When waiting for a new message to be received on a connection</li>
     * <li>When waiting for a new message to be sent on a connection</li>
     * </ul>
     * <p>This value is interpreted as the maximum time between some progress being made on the connection.
     * So if a single byte is read or written, then the timeout is reset.</p>
     *
     * 设置连接的最大空闲时间
     *
     * @param idleTimeout the idle timeout
     */
    public void setIdleTimeout(long idleTimeout) {
        _idleTimeout = idleTimeout;
    }

    /**
     * 获取接收器
     *
     * @return Returns the number of acceptor threads.
     */
    @ManagedAttribute("number of acceptor threads")
    public int getAcceptors() {
        return _acceptors.length;
    }

    /**
     * 启动
     *
     * @throws Exception
     */
    @Override
    protected void doStart() throws Exception {
        // 默认协议
        if(_defaultProtocol == null) {
            throw new IllegalStateException("No default protocol for "+this);
        }

        // 操作默认协议的默认连接工厂
        _defaultConnectionFactory = getConnectionFactory(_defaultProtocol);
        if(_defaultConnectionFactory==null) {
            throw new IllegalStateException("No protocol factory for default protocol '"+_defaultProtocol+"' in "+this);
        }

        // 对ssl的支持
        SslConnectionFactory ssl = getConnectionFactory(SslConnectionFactory.class);
        if (ssl != null) {
            String next = ssl.getNextProtocol();
            ConnectionFactory cf = getConnectionFactory(next);
            if (cf == null) {
                throw new IllegalStateException("No protocol factory for SSL next protocol: '" + next + "' in " + this);
            }
        }

        super.doStart();

        // 关闭时使用
        _stopping = new CountDownLatch(_acceptors.length);

        // 创建接收器
        for (int i = 0; i < _acceptors.length; i++) {
            Acceptor a = new Acceptor(i);
            addBean(a);
            getExecutor().execute(a);
        }

        LOG.info("Started {}", this);
    }


    /**
     * 打断接收
     */
    protected void interruptAcceptors() {
        try (Locker.Lock lock = _locker.lockIfNotHeld()) {
            for (Thread thread : _acceptors) {
                if (thread != null) {
                    thread.interrupt();
                }
            }
        }
    }

    /**
     * 关闭
     *
     * @return
     */
    @Override
    public Future<Void> shutdown() {
        return new FutureCallback(true);
    }

    /**
     * 结束
     *
     * @throws Exception
     */
    @Override
    protected void doStop() throws Exception {
        // Tell the acceptors we are stopping
        interruptAcceptors();

        // If we have a stop timeout
        long stopTimeout = getStopTimeout();
        CountDownLatch stopping=_stopping;
        if (stopTimeout > 0 && stopping!=null && getAcceptors()>0)
            stopping.await(stopTimeout,TimeUnit.MILLISECONDS);
        _stopping=null;

        super.doStop();

        for (Acceptor a : getBeans(Acceptor.class))
            removeBean(a);

        LOG.info("Stopped {}", this);
    }

    /**
     * 同步
     *
     * @throws InterruptedException
     */
    public void join() throws InterruptedException {
        join(0);
    }

    /**
     * 同步
     *
     * @param timeout
     * @throws InterruptedException
     */
    public void join(long timeout) throws InterruptedException {
        try (Locker.Lock lock = _locker.lock()) {
            for (Thread thread : _acceptors)
                if (thread != null) {
                    thread.join(timeout);
                }
        }
    }

    /**
     * 接受一个请求
     */
    protected abstract void accept(int acceptorID) throws IOException, InterruptedException;


    /**
     * 是不是正在接受连接
     *
     * @return Is the connector accepting new connections
     */
    public boolean isAccepting() {
        try (Locker.Lock lock = _locker.lock()) {
            return _accepting;
        }
    }

    /**
     * 设置接受连接的状态
     *
     * @param accepting
     */
    public void setAccepting(boolean accepting) {
        try (Locker.Lock lock = _locker.lock()) {
            _accepting = accepting;
            _setAccepting.signalAll();
        }
    }

    /**
     * 连接工厂
     */
    @Override
    public ConnectionFactory getConnectionFactory(String protocol) {
        try (Locker.Lock lock = _locker.lock()) {
            return _factories.get(StringUtil.asciiToLowerCase(protocol));
        }
    }

    /**
     * 获取某个类型的连接工厂
     *
     * @param factoryType
     * @param <T>
     * @return
     */
    @Override
    public <T> T getConnectionFactory(Class<T> factoryType) {
        try (Locker.Lock lock = _locker.lock()) {
            for (ConnectionFactory f : _factories.values())
                if (factoryType.isAssignableFrom(f.getClass())) {
                    return (T)f;
                }
            return null;
        }
    }

    /**
     * 添加连接工厂
     *
     * @param factory
     */
    public void addConnectionFactory(ConnectionFactory factory) {
        try (Locker.Lock lock = _locker.lockIfNotHeld()) {
            Set<ConnectionFactory> to_remove = new HashSet<>();
            for (String key:factory.getProtocols()) {
                key = StringUtil.asciiToLowerCase(key);
                ConnectionFactory old = _factories.remove(key);
                if (old != null) {
                    if (old.getProtocol().equals(_defaultProtocol)) {
                        _defaultProtocol=null;
                    }
                    to_remove.add(old);
                }
                _factories.put(key, factory);
            }

            // keep factories still referenced
            for (ConnectionFactory f : _factories.values()) {
                to_remove.remove(f);
            }

            // remove old factories
            for (ConnectionFactory old: to_remove) {
                removeBean(old);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} removed {}", this, old);
                }
            }

            // add new Bean
            addBean(factory);
            if (_defaultProtocol == null) {
                _defaultProtocol = factory.getProtocol();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} added {}", this, factory);
            }
        }
    }

    /**
     * 添加为第一个连接工厂
     *
     * @param factory
     */
    public void addFirstConnectionFactory(ConnectionFactory factory) {
        try (Locker.Lock lock = _locker.lock()) {
            List<ConnectionFactory> existings = new ArrayList<>(_factories.values());
            _factories.clear();
            addConnectionFactory(factory);
            for (ConnectionFactory existing : existings) {
                addConnectionFactory(existing);
            }
            _defaultProtocol = factory.getProtocol();
        }
    }

    /**
     * 如果不存在就添加连接工厂
     * 相当于一个兜底方案
     *
     * @param factory
     */
    public void addIfAbsentConnectionFactory(ConnectionFactory factory) {
        try (Locker.Lock lock = _locker.lock()) {
            String key = StringUtil.asciiToLowerCase(factory.getProtocol());
            if (_factories.containsKey(key)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} addIfAbsent ignored {}", this, factory);
                }
            } else {
                _factories.put(key, factory);
                addBean(factory);
                if (_defaultProtocol==null) {
                    _defaultProtocol=factory.getProtocol();
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} addIfAbsent added {}", this, factory);
                }
            }
        }
    }

    /**
     * 移除指定协议的连接工厂
     *
     * @param protocol
     * @return
     */
    public ConnectionFactory removeConnectionFactory(String protocol) {
        try (Locker.Lock lock = _locker.lock()) {
            ConnectionFactory factory= _factories.remove(StringUtil.asciiToLowerCase(protocol));
            removeBean(factory);
            return factory;
        }
    }

    /**
     * 获取连接工厂
     *
     * @return
     */
    @Override
    public Collection<ConnectionFactory> getConnectionFactories() {
        try (Locker.Lock lock = _locker.lock()) {
            return _factories.values();
        }
    }

    /**
     * 设置连接工厂
     *
     * @param factories
     */
    public void setConnectionFactories(Collection<ConnectionFactory> factories) {
        try (Locker.Lock lock = _locker.lock()) {
            List<ConnectionFactory> existing = new ArrayList<>(_factories.values());
            for (ConnectionFactory factory: existing) {
                removeConnectionFactory(factory.getProtocol());
            }
            for (ConnectionFactory factory: factories) {
                if (factory!=null) {
                    addConnectionFactory(factory);
                }
            }
        }
    }

    /**
     * 获取优先级
     *
     * @return
     */
    @ManagedAttribute("The priority delta to apply to acceptor threads")
    public int getAcceptorPriorityDelta() {
        return _acceptorPriorityDelta;
    }

    /* ------------------------------------------------------------ */
    /** Set the acceptor thread priority delta.
     * <p>This allows the acceptor thread to run at a different priority.
     * Typically this would be used to lower the priority to give preference
     * to handling previously accepted connections rather than accepting
     * new connections</p>
     *
     * 设置优先级
     *
     * @param acceptorPriorityDelta the acceptor priority delta
     */
    public void setAcceptorPriorityDelta(int acceptorPriorityDelta) {
        int old=_acceptorPriorityDelta;
        _acceptorPriorityDelta = acceptorPriorityDelta;
        if (old!=acceptorPriorityDelta && isStarted()) {
            for (Thread thread : _acceptors) {
                thread.setPriority(Math.max(Thread.MIN_PRIORITY,Math.min(Thread.MAX_PRIORITY,thread.getPriority()-old+acceptorPriorityDelta)));
            }
        }
    }

    /**
     * 获取所有的协议
     */
    @Override
    @ManagedAttribute("Protocols supported by this connector")
    public List<String> getProtocols() {
        synchronized (_factories) {
            return new ArrayList<>(_factories.keySet());
        }
    }

    /**
     * 清理所有的连接工厂
     */
    public void clearConnectionFactories() {
        synchronized (_factories) {
            _factories.clear();
        }
    }

    /**
     * 获取默认的协议
     *
     * @return
     */
    @ManagedAttribute("This connector's default protocol")
    public String getDefaultProtocol() {
        return _defaultProtocol;
    }

    /**
     * 设置默认的协议
     *
     * @param defaultProtocol
     */
    public void setDefaultProtocol(String defaultProtocol) {
        _defaultProtocol = StringUtil.asciiToLowerCase(defaultProtocol);
        if (isRunning()) {
            _defaultConnectionFactory=getConnectionFactory(_defaultProtocol);
        }
    }

    /**
     * 获取默认的连接工厂
     *
     * @return
     */
    @Override
    public ConnectionFactory getDefaultConnectionFactory() {
        if (isStarted()) {
            return _defaultConnectionFactory;
        }
        return getConnectionFactory(_defaultProtocol);
    }

    /**
     * 处理接受失败
     *
     * @param ex
     * @return
     */
    protected boolean handleAcceptFailure(Throwable ex) {
        if (isRunning()) {
            if (ex instanceof InterruptedException) {
                LOG.debug(ex);
                return true;
            }

            if (ex instanceof ClosedByInterruptException) {
                LOG.debug(ex);
                return false;
            }
            
            LOG.warn(ex);
            try {
                // Arbitrary sleep to avoid spin looping.
                // Subclasses may decide for a different
                // sleep policy or closing the connector.
                Thread.sleep(1000);
                return true;
            } catch (Throwable x) {
                LOG.ignore(x);
            }
            return false;
        } else {
            LOG.ignore(ex);
            return false;
        }
    }

    /**
     * 接收器
     */
    private class Acceptor implements Runnable {
        private final int _id;
        private String _name;

        private Acceptor(int id) {
            _id = id;
        }

        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            String name=thread.getName();
            _name=String.format("%s-acceptor-%d@%x-%s",name,_id,hashCode(),AbstractConnector.this.toString());
            thread.setName(_name);

            int priority=thread.getPriority();
            if (_acceptorPriorityDelta!=0)
                thread.setPriority(Math.max(Thread.MIN_PRIORITY,Math.min(Thread.MAX_PRIORITY,priority+_acceptorPriorityDelta)));

            synchronized (AbstractConnector.this) {
                _acceptors[_id] = thread;
            }

            try {
                while (isRunning()) {
                    try (Locker.Lock lock = _locker.lock()) {
                        if (!_accepting && isRunning()) {
                            _setAccepting.await();
                            continue;
                        }
                    } catch (InterruptedException e) {
                        continue;
                    }
                    
                    try {
                        accept(_id);
                    } catch (Throwable x) {
                        if (!handleAcceptFailure(x))
                            break;
                    }
                }
            } finally {
                thread.setName(name);
                if (_acceptorPriorityDelta!=0) {
                    thread.setPriority(priority);
                }

                synchronized (AbstractConnector.this) {
                    _acceptors[_id] = null;
                }
                CountDownLatch stopping=_stopping;
                if (stopping!=null) {
                    stopping.countDown();
                }
            }
        }

        @Override
        public String toString() {
            String name=_name;
            if (name==null)
                return String.format("acceptor-%d@%x", _id, hashCode());
            return name;
        }
    }


//    protected void connectionOpened(Connection connection)
//    {
//        _stats.connectionOpened();
//        connection.onOpen();
//    }
//
//    protected void connectionClosed(Connection connection)
//    {
//        connection.onClose();
//        long duration = System.currentTimeMillis() - connection.getEndPoint().getCreatedTimeStamp();
//        _stats.connectionClosed(duration, connection.getMessagesIn(), connection.getMessagesOut());
//    }
//
//    public void connectionUpgraded(Connection oldConnection, Connection newConnection)
//    {
//        oldConnection.onClose();
//        _stats.connectionUpgraded(oldConnection.getMessagesIn(), oldConnection.getMessagesOut());
//        newConnection.onOpen();
//    }

    /**
     * 获取连接的所有终端
     *
     * @return
     */
    @Override
    public Collection<EndPoint> getConnectedEndPoints() {
        return _immutableEndPoints;
    }

    /**
     * 在终端开启时的回调
     *
     * @param endp
     */
    protected void onEndPointOpened(EndPoint endp) {
        _endpoints.add(endp);
    }

    /**
     * 在终端关闭时的回调
     *
     * @param endp
     */
    protected void onEndPointClosed(EndPoint endp) {
        _endpoints.remove(endp);
    }

    /**
     * 获取调度器
     */
    @Override
    public Scheduler getScheduler() {
        return _scheduler;
    }

    /**
     * 获取名称
     *
     * @return
     */
    @Override
    public String getName() {
        return _name;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set a connector name.   A context may be configured with
     * virtual hosts in the form "@contextname" and will only serve
     * requests from the named connector,
     *
     * 设置名称
     *
     * @param name A connector name.
     */
    public void setName(String name) {
        _name=name;
    }

    /**
     * 转换为字符串格式
     *
     * @return
     */
    @Override
    public String toString() {
        return String.format("%s@%x{%s,%s}",
                _name==null?getClass().getSimpleName():_name,
                hashCode(),
                getDefaultProtocol(),getProtocols());
    }
}
