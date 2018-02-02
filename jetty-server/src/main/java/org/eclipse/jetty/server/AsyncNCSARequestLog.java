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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;


/* ------------------------------------------------------------ */
/**
 * An asynchronously writing NCSA Request Log
 *
 * 异步的NCSA格式的请求日志
 * 其实这里的思路很简单，就是把日志写入到阻塞队列中，然后循环去阻塞队列中去获取
 * 这种方式新开了一个线程，实现了线程隔离
 */
public class AsyncNCSARequestLog extends NCSARequestLog {

    /**
     * 日志类
     */
    private static final Logger LOG = Log.getLogger(AsyncNCSARequestLog.class);

    /**
     * 阻塞队列
     */
    private final BlockingQueue<String> _queue;

    /**
     * 写线程
     */
    private transient WriterThread _thread;

    /**
     * 逸出
     */
    private boolean _warnedFull;

    /**
     * 构造方法
     */
    public AsyncNCSARequestLog() {
        this(null,null);
    }

    /**
     * 构造方法
     *
     * @param queue
     */
    public AsyncNCSARequestLog(BlockingQueue<String> queue) {
        this(null,queue);
    }

    /**
     * 构造方法
     *
     * @param filename
     */
    public AsyncNCSARequestLog(String filename) {
        this(filename,null);
    }

    /**
     * 构造方法
     *
     * @param filename
     * @param queue
     */
    public AsyncNCSARequestLog(String filename,BlockingQueue<String> queue) {
        super(filename);
        if (queue==null) {
            queue=new BlockingArrayQueue<>(1024);
        }
        _queue=queue;
    }

    /**
     * 写线程
     */
    private class WriterThread extends Thread {
        /**
         * 构造方法
         */
        WriterThread() {
            setName("AsyncNCSARequestLog@"+Integer.toString(AsyncNCSARequestLog.this.hashCode(),16));
        }

        /**
         * 具体执行
         */
        @Override
        public void run() {
            while (isRunning()) {
                try {
                    String log = _queue.poll(10,TimeUnit.SECONDS);
                    if (log != null) {
                        AsyncNCSARequestLog.super.write(log);
                    }

                    while(!_queue.isEmpty()) {
                        log = _queue.poll();
                        if (log!=null)
                            AsyncNCSARequestLog.super.write(log);
                    }
                } catch (IOException e) {
                    LOG.warn(e);
                } catch (InterruptedException e) {
                    LOG.ignore(e);
                }
            }
        }
    }

    /**
     * 启动
     *
     * @throws Exception
     */
    @Override
    protected synchronized void doStart() throws Exception {
        super.doStart();
        _thread = new WriterThread();
        _thread.start();
    }

    /**
     * 停止
     *
     * @throws Exception
     */
    @Override
    protected void doStop() throws Exception {
        _thread.interrupt();
        _thread.join();
        super.doStop();
        _thread=null;
    }

    /**
     * 写入日志
     *
     * @param log
     * @throws IOException
     */
    @Override
    public void write(String log) throws IOException {
        if (!_queue.offer(log)) {
            if (_warnedFull) {
                LOG.warn("Log Queue overflow");
            }
            _warnedFull=true;
        }
    }

}
