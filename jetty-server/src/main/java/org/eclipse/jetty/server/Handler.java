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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.component.Destroyable;
import org.eclipse.jetty.util.component.LifeCycle;

/** A Jetty Server Handler.
 * <p>
 * A Handler instance is required by a {@link Server} to handle incoming
 * HTTP requests.
 * <p>  
 * A Handler may: 
 * <ul>
 * <li>Completely generate the HTTP Response</li>
 * <li>Examine/modify the request and call another Handler (see {@link HandlerWrapper}).
 * <li>Pass the request to one or more other Handlers (see {@link HandlerCollection}).
 * </ul>
 *
 * Handlers are passed the servlet API request and response object, but are
 * not Servlets.  The servlet container is implemented by handlers for
 * context, security, session and servlet that modify the request object
 * before passing it to the next stage of handling.
 *
 * 处理器
 * 处理器可以处理http请求
 *
 * 处理器可以执行的操作:
 * 1.生成http响应
 * 2.检验或者修改请求并且调用另一个处理器
 * 3.把这个请求分发给一个或者多个处理器
 *
 * 处理器传递servlet api中的request和response对象
 * 但是处理器不是servlet
 *
 */
@ManagedObject("Jetty Handler")
public interface Handler extends LifeCycle, Destroyable {
    /**
     * Handle a request.
     *
     * 处理一个请求
     *
     * @param target
     *            The target of the request - either a URI or a name.
     * @param baseRequest
     *            The original unwrapped request object.
     * @param request
     *            The request either as the {@link Request} object or a wrapper of that request. The
     *            <code>{@link HttpConnection#getCurrentConnection()}.{@link HttpConnection#getHttpChannel() getHttpChannel()}.{@link HttpChannel#getRequest() getRequest()}</code>
     *            method can be used access the Request object if required.
     * @param response
     *            The response as the {@link Response} object or a wrapper of that request. The
     *            <code>{@link HttpConnection#getCurrentConnection()}.{@link HttpConnection#getHttpChannel() getHttpChannel()}.{@link HttpChannel#getResponse() getResponse()}</code>
     *            method can be used access the Response object if required.
     * @throws IOException
     *             if unable to handle the request or response processing
     * @throws ServletException
     *             if unable to handle the request or response due to underlying servlet issue
     */
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;

    /**
     * 设置服务器
     *
     * @param server
     */
    public void setServer(Server server);

    /**
     * 获取服务器
     *
     * @return
     */
    @ManagedAttribute(value="the jetty server for this handler", readonly=true)
    public Server getServer();

    /**
     * 销毁
     */
    @ManagedOperation(value="destroy associated resources", impact="ACTION")
    public void destroy();
}

