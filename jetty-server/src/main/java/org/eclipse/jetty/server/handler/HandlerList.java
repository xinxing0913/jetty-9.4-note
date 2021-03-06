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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;

/* ------------------------------------------------------------ */
/** HandlerList.
 * This extension of {@link HandlerCollection} will call
 * each contained handler in turn until either an exception is thrown, the response
 * is committed or a positive response status is set.
 *
 * 处理器列表
 * 在有如下场景的时候，它会执行结束:
 * 1.有一个处理器已经把请求标记为处理结束
 * 2.有一个处理器抛出了异常
 *
 */
public class HandlerList extends HandlerCollection {

    /**
     * 构造方法
     */
    public HandlerList() {
    }

    /**
     * 处理器列表
     *
     * @param handlers
     */
    public HandlerList(Handler... handlers) {
        super(handlers);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * 执行处理
     *
     * @see Handler#handle(String, Request, HttpServletRequest, HttpServletResponse)
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        Handler[] handlers = getHandlers();

        if (handlers!=null && isStarted()) {
            for (int i=0;i<handlers.length;i++) {
                handlers[i].handle(target,baseRequest, request, response);
                // 如果已经被处理了，则不再执行剩下的处理
                if ( baseRequest.isHandled()) {
                    return;
                }
            }
        }
    }
}
