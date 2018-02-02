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

import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * A Handler that contains other Handlers.
 * <p>
 * The contained handlers may be one (see @{link {@link org.eclipse.jetty.server.handler.HandlerWrapper})
 * or many (see {@link org.eclipse.jetty.server.handler.HandlerList} or {@link org.eclipse.jetty.server.handler.HandlerCollection}. 
 *
 * 处理器容器
 */
@ManagedObject("Handler of Multiple Handlers")
public interface HandlerContainer extends LifeCycle {
    /* ------------------------------------------------------------ */
    /**
     * 获取所有的处理器
     *
     * @return array of handlers directly contained by this handler.
     */
    @ManagedAttribute("handlers in this container")
    public Handler[] getHandlers();
    
    /* ------------------------------------------------------------ */
    /**
     * 获取子处理器
     *
     * @return array of all handlers contained by this handler and it's children
     */
    @ManagedAttribute("all contained handlers")
    public Handler[] getChildHandlers();
    
    /* ------------------------------------------------------------ */
    /**
     * 根据类来获取子处理器
     *
     *
     * @param byclass the child handler class to get
     * @return array of all handlers contained by this handler and it's children of the passed type.
     */
    public Handler[] getChildHandlersByClass(Class<?> byclass);
    
    /* ------------------------------------------------------------ */
    /**
     * 根据类型来获取子处理器
     *
     * @param byclass the child handler class to get
     * @return first handler of all handlers contained by this handler and it's children of the passed type.
     * @param <T> the type of handler
     */
    public <T extends Handler> T getChildHandlerByClass(Class<T> byclass);
}
