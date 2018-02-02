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
import java.util.Locale;

import javax.servlet.http.Cookie;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.pathmap.PathMappings;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.DateCache;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Base implementation of the {@link RequestLog} outputs logs in the pseudo-standard NCSA common log format.
 * Configuration options allow a choice between the standard Common Log Format (as used in the 3 log format) and the
 * Combined Log Format (single log format). This log format can be output by most web servers, and almost all web log
 * analysis software can understand these formats.
 *
 * 抽象的NCSA格式的请求日志
 */
public abstract class AbstractNCSARequestLog extends AbstractLifeCycle implements RequestLog {
    /**
     * 日志类
     */
    protected static final Logger LOG = Log.getLogger(AbstractNCSARequestLog.class);

    /**
     * 当前线程内的StringBuilder缓存
     */
    private static ThreadLocal<StringBuilder> _buffers = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder(256);
        }
    };

    /**
     * 忽略的路径
     */
    private String[] _ignorePaths;

    /**
     * 是否显示扩展信息，即Referer和User_Agent
     */
    private boolean _extended;

    /**
     * 忽略的请求映射
     */
    private transient PathMappings<String> _ignorePathMap;

    /**
     * 记录执行时间
     */
    private boolean _logLatency = false;

    /**
     * 记录cookie
     */
    private boolean _logCookies = false;

    /**
     * 记录服务器信息
     */
    private boolean _logServer = false;

    /**
     * 地址的前置地址，即X-Forwarded-For的值
     */
    private boolean _preferProxiedForAddress;

    /**
     * 时间缓存
     */
    private transient DateCache _logDateCache;

    /**
     * 日期格式
     */
    private String _logDateFormat = "dd/MMM/yyyy:HH:mm:ss Z";

    /**
     * 本地
     */
    private Locale _logLocale = Locale.getDefault();

    /**
     * 时区
     */
    private String _logTimeZone = "GMT";

    /* ------------------------------------------------------------ */

    /**
     * Is logging enabled
     *
     * 是否开启日志
     *
     * @return true if logging is enabled
     */
    protected abstract boolean isEnabled();

    /* ------------------------------------------------------------ */

    /**
     * Write requestEntry out. (to disk or slf4j log)
     *
     * 写入请求实体
     *
     * @param requestEntry the request entry
     * @throws IOException if unable to write the entry
     */
    public abstract void write(String requestEntry) throws IOException;

    /* ------------------------------------------------------------ */

    /**
     * 追加日志
     *
     * @param buf
     * @param s
     */
    private void append(StringBuilder buf,String s) {
        if (s==null || s.length()==0) {
            buf.append('-');
        } else {
            buf.append(s);
        }
    }

    /**
     * Writes the request and response information to the output stream.
     *
     * @see org.eclipse.jetty.server.RequestLog#log(Request, Response)
     */
    @Override
    public void log(Request request, Response response) {
        try {
            // 是否忽略这一类日志
            if (_ignorePathMap != null && _ignorePathMap.getMatch(request.getRequestURI()) != null) {
                return;
            }

            // 是否开启了日志
            if (!isEnabled()) {
                return;
            }

            StringBuilder buf = _buffers.get();
            buf.setLength(0);

            // 记录服务器信息
            if (_logServer) {
                append(buf,request.getServerName());
                buf.append(' ');
            }

            String addr = null;
            if (_preferProxiedForAddress) {
                addr = request.getHeader(HttpHeader.X_FORWARDED_FOR.toString());
            }

            if (addr == null) {
                addr = request.getRemoteAddr();
            }

            buf.append(addr);
            buf.append(" - ");
            Authentication authentication = request.getAuthentication();
            append(buf,(authentication instanceof Authentication.User)?((Authentication.User)authentication).getUserIdentity().getUserPrincipal().getName():null);

            buf.append(" [");
            if (_logDateCache != null) {
                buf.append(_logDateCache.format(request.getTimeStamp()));
            } else {
                buf.append(request.getTimeStamp());
            }

            buf.append("] \"");
            append(buf,request.getMethod());
            buf.append(' ');
            append(buf,request.getOriginalURI());
            buf.append(' ');
            append(buf,request.getProtocol());
            buf.append("\" ");

            int status = response.getCommittedMetaData().getStatus();
            if (status >=0) {
                buf.append((char)('0' + ((status / 100) % 10)));
                buf.append((char)('0' + ((status / 10) % 10)));
                buf.append((char)('0' + (status % 10)));
            } else {
                buf.append(status);
            }

            long written = response.getHttpChannel().getBytesWritten();
            if (written >= 0) {
                buf.append(' ');
                if (written > 99999) {
                    buf.append(written);
                } else {
                    if (written > 9999)
                        buf.append((char)('0' + ((written / 10000) % 10)));
                    if (written > 999)
                        buf.append((char)('0' + ((written / 1000) % 10)));
                    if (written > 99)
                        buf.append((char)('0' + ((written / 100) % 10)));
                    if (written > 9)
                        buf.append((char)('0' + ((written / 10) % 10)));
                    buf.append((char)('0' + (written) % 10));
                }
                buf.append(' ');
            } else {
                buf.append(" - ");
            }

            // 记录扩展信息
            if (_extended) {
                logExtended(buf, request, response);
            }

            // 记录cookie
            if (_logCookies) {
                Cookie[] cookies = request.getCookies();
                if (cookies == null || cookies.length == 0) {
                    buf.append(" -");
                } else {
                    buf.append(" \"");
                    for (int i = 0; i < cookies.length; i++) {
                        if (i != 0) {
                            buf.append(';');
                        }
                        buf.append(cookies[i].getName());
                        buf.append('=');
                        buf.append(cookies[i].getValue());
                    }
                    buf.append('\"');
                }
            }

            // 记录执行时间
            if (_logLatency) {
                long now = System.currentTimeMillis();

                if (_logLatency) {
                    buf.append(' ');
                    buf.append(now - request.getTimeStamp());
                }
            }

            String log = buf.toString();
            write(log);
        } catch (IOException e) {
            LOG.warn(e);
        }
    }

    /**
     * Writes extended request and response information to the output stream.
     *
     * 是否添加扩展信息
     * 这里包含两个，一个是Referer，一个是User_Agent
     *
     * @param b        StringBuilder to write to
     * @param request  request object
     * @param response response object
     * @throws IOException if unable to log the extended information
     */
    protected void logExtended(StringBuilder b, Request request, Response response) throws IOException {
        String referer = request.getHeader(HttpHeader.REFERER.toString());
        if (referer == null) {
            b.append("\"-\" ");
        } else {
            b.append('"');
            b.append(referer);
            b.append("\" ");
        }

        String agent = request.getHeader(HttpHeader.USER_AGENT.toString());
        if (agent == null) {
            b.append("\"-\"");
        } else {
            b.append('"');
            b.append(agent);
            b.append('"');
        }
    }

    /**
     * Set request paths that will not be logged.
     *
     * 设置忽略的路径
     *
     * @param ignorePaths array of request paths
     */
    public void setIgnorePaths(String[] ignorePaths)
    {
        _ignorePaths = ignorePaths;
    }

    /**
     * Retrieve the request paths that will not be logged.
     *
     * 获取忽略的路径
     *
     * @return array of request paths
     */
    public String[] getIgnorePaths() {
        return _ignorePaths;
    }

    /**
     * Controls logging of the request cookies.
     *
     * 设置是否记录cookie
     *
     * @param logCookies true - values of request cookies will be logged, false - values of request cookies will not be
     *                   logged
     */
    public void setLogCookies(boolean logCookies) {
        _logCookies = logCookies;
    }

    /**
     * Retrieve log cookies flag
     *
     * 是否记录cookie
     *
     * @return value of the flag
     */
    public boolean getLogCookies() {
        return _logCookies;
    }

    /**
     * Controls logging of the request hostname.
     *
     * 设置是否记录主机名
     *
     * @param logServer true - request hostname will be logged, false - request hostname will not be logged
     */
    public void setLogServer(boolean logServer) {
        _logServer = logServer;
    }

    /**
     * Retrieve log hostname flag.
     *
     * 记录主机名
     *
     * @return value of the flag
     */
    public boolean getLogServer() {
        return _logServer;
    }

    /**
     * Controls logging of request processing time.
     *
     * 设置执行时间
     *
     * @param logLatency true - request processing time will be logged false - request processing time will not be
     *                   logged
     */
    public void setLogLatency(boolean logLatency) {
        _logLatency = logLatency;
    }

    /**
     * Retrieve log request processing time flag.
     *
     * 获取执行时间
     *
     * @return value of the flag
     */
    public boolean getLogLatency() {
        return _logLatency;
    }

    /**
     * 以废弃
     *
     * @param value true to log dispatch
     * @deprecated use {@link StatisticsHandler}
     */
    @Deprecated
    public void setLogDispatch(boolean value) {
    }

    /**
     * 已废弃
     *
     * @return true if logging dispatches
     * @deprecated use {@link StatisticsHandler}
     */
    @Deprecated
    public boolean isLogDispatch() {
        return false;
    }

    /**
     * Controls whether the actual IP address of the connection or the IP address from the X-Forwarded-For header will
     * be logged.
     *
     * 设置是否记录真实的IP地址
     *
     * @param preferProxiedForAddress true - IP address from header will be logged, false - IP address from the
     *                                connection will be logged
     */
    public void setPreferProxiedForAddress(boolean preferProxiedForAddress) {
        _preferProxiedForAddress = preferProxiedForAddress;
    }

    /**
     * Retrieved log X-Forwarded-For IP address flag.
     *
     * 是否记录X-Forwarded-For IP地址
     *
     * @return value of the flag
     */
    public boolean getPreferProxiedForAddress() {
        return _preferProxiedForAddress;
    }

    /**
     * Set the extended request log format flag.
     *
     * 设置是否填写扩展信息
     *
     * @param extended true - log the extended request information, false - do not log the extended request information
     */
    public void setExtended(boolean extended) {
        _extended = extended;
    }

    /**
     * Retrieve the extended request log format flag.
     *
     * 获取是否填写扩展信息
     *
     * @return value of the flag
     */
    @ManagedAttribute("use extended NCSA format")
    public boolean isExtended() {
        return _extended;
    }

    /**
     * Set up request logging and open log file.
     *
     * 开始
     *
     * @see org.eclipse.jetty.util.component.AbstractLifeCycle#doStart()
     */
    @Override
    protected synchronized void doStart() throws Exception {
        if (_logDateFormat != null) {
            _logDateCache = new DateCache(_logDateFormat, _logLocale ,_logTimeZone);
        }

        if (_ignorePaths != null && _ignorePaths.length > 0) {
            _ignorePathMap = new PathMappings<>();
            for (int i = 0; i < _ignorePaths.length; i++) {
                _ignorePathMap.put(_ignorePaths[i], _ignorePaths[i]);
            }
        } else {
            _ignorePathMap = null;
        }

        super.doStart();
    }

    /**
     * 结束
     *
     * @throws Exception
     */
    @Override
    protected void doStop() throws Exception {
        _logDateCache = null;
        super.doStop();
    }

    /**
     * Set the timestamp format for request log entries in the file. If this is not set, the pre-formated request
     * timestamp is used.
     *
     * 设置时间格式
     *
     * @param format timestamp format string
     */
    public void setLogDateFormat(String format) {
        _logDateFormat = format;
    }

    /**
     * Retrieve the timestamp format string for request log entries.
     *
     * 获取时间格式
     *
     * @return timestamp format string.
     */
    public String getLogDateFormat() {
        return _logDateFormat;
    }

    /**
     * Set the locale of the request log.
     *
     * 设置本地表示
     *
     * @param logLocale locale object
     */
    public void setLogLocale(Locale logLocale) {
        _logLocale = logLocale;
    }

    /**
     * Retrieve the locale of the request log.
     *
     * 获取本地表示
     *
     * @return locale object
     */
    public Locale getLogLocale() {
        return _logLocale;
    }

    /**
     * Set the timezone of the request log.
     *
     * 设置时区
     *
     * @param tz timezone string
     */
    public void setLogTimeZone(String tz) {
        _logTimeZone = tz;
    }

    /**
     * Retrieve the timezone of the request log.
     *
     * 获取时区
     *
     * @return timezone string
     */
    @ManagedAttribute("the timezone")
    public String getLogTimeZone() {
        return _logTimeZone;
    }
}
