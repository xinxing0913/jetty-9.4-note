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

package org.eclipse.jetty.webapp;

/**
 * 抽象配置
 * 提供的都是空实现
 */
public class AbstractConfiguration implements Configuration {

    /**
     * 配置前
     *
     * @param context The context to configure
     * @throws Exception
     */
    public void preConfigure(WebAppContext context) throws Exception {
    }

    /**
     * 执行配置
     *
     * @param context The context to configure
     * @throws Exception
     */
    public void configure(WebAppContext context) throws Exception {
    }

    /**
     * 配置后
     *
     * @param context The context to configure
     * @throws Exception
     */
    public void postConfigure(WebAppContext context) throws Exception {
    }

    /**
     * 清空配置
     *
     * @param context The context to configure
     * @throws Exception
     */
    public void deconfigure(WebAppContext context) throws Exception {
    }

    /**
     * 销毁
     *
     * @param context The context to configure
     * @throws Exception
     */
    public void destroy(WebAppContext context) throws Exception {
    }

    /**
     * 克隆配置
     *
     * @param template The template context
     * @param context The context to configure
     * @throws Exception
     */
    public void cloneConfigure(WebAppContext template, WebAppContext context) throws Exception {
    }
}
