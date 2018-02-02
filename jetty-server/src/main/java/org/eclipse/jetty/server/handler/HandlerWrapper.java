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

package org.eclipse.jetty.server.handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.LifeCycle;

/* ------------------------------------------------------------ */
/** A <code>HandlerWrapper</code> acts as a {@link Handler} but delegates the {@link Handler#handle handle} method and
 * {@link LifeCycle life cycle} events to a delegate. This is primarily used to implement the <i>Decorator</i> pattern.
 *
 * 处理器包装类
 * 它实现了代理，并且可以进行装饰
 */
@ManagedObject("Handler wrapping another Handler")
public class HandlerWrapper extends AbstractHandlerContainer {

    /**
     * 代理的控制器
     */
    protected Handler _handler;

    /* ------------------------------------------------------------ */
    /**
     * 构造方法
     */
    public HandlerWrapper() {
    }

    /* ------------------------------------------------------------ */
    /**
     * 获取原处理器
     *
     * @return Returns the handlers.
     */
    @ManagedAttribute(value="Wrapped Handler", readonly=true)
    public Handler getHandler() {
        return _handler;
    }

    /* ------------------------------------------------------------ */
    /**
     * 获取所有的处理器
     *
     * @return Returns the handlers.
     */
    @Override
    public Handler[] getHandlers() {
        if (_handler==null) {
            return new Handler[0];
        }
        return new Handler[] {_handler};
    }

    /* ------------------------------------------------------------ */
    /**
     * 设置代理的处理器
     *
     * @param handler Set the {@link Handler} which should be wrapped.
     */
    public void setHandler(Handler handler) {
        if (isStarted()) {
            throw new IllegalStateException(STARTED);
        }

        // check for loops
        // 检测循环
        if (handler==this || (handler instanceof HandlerContainer &&
            Arrays.asList(((HandlerContainer)handler).getChildHandlers()).contains(this))) {
            throw new IllegalStateException("setHandler loop");
        }
        
        if (handler!=null) {
            handler.setServer(getServer());
        }
        
        Handler old=_handler;
        _handler=handler;
        updateBean(old,_handler,true);
    }

    /* ------------------------------------------------------------ */
    /** 
     * Replace the current handler with another HandlerWrapper
     * linked to the current handler.  
     * <p>
     * This is equivalent to:
     * <pre>
     *   wrapper.setHandler(getHandler());
     *   setHandler(wrapper);
     * </pre>
     *
     * 插入另一个包装类的处理器
     *
     * @param wrapper the wrapper to insert
     */
    public void insertHandler(HandlerWrapper wrapper) {
        if (wrapper==null) {
            throw new IllegalArgumentException();
        }
        
        HandlerWrapper tail = wrapper;
        while(tail.getHandler() instanceof HandlerWrapper) {
            tail=(HandlerWrapper)tail.getHandler();
        }
        if (tail.getHandler() != null) {
            throw new IllegalArgumentException("bad tail of inserted wrapper chain");
        }
        
        Handler next = getHandler();
        setHandler(wrapper);
        tail.setHandler(next);
    }

    /* ------------------------------------------------------------ */

    /**
     * 处理
     *
     * @param target
     * @param baseRequest
     * @param request
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Handler handler = _handler;
        if (handler!=null) {
            handler.handle(target,baseRequest, request, response);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * 扩张子链
     *
     * @param list
     * @param byClass
     */
    @Override
    protected void expandChildren(List<Handler> list, Class<?> byClass) {
        expandHandler(_handler, list, byClass);
    }

    /* ------------------------------------------------------------ */

    /**
     * 销毁
     */
    @Override
    public void destroy() {
        if (!isStopped()) {
            throw new IllegalStateException("!STOPPED");
        }
        Handler child=getHandler();
        if (child!=null) {
            setHandler(null);
            child.destroy();
        }
        super.destroy();
    }

}
