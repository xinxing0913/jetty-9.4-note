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

package org.eclipse.jetty.deploy;

import java.io.IOException;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * Object responsible for providing {@link App}s to the {@link DeploymentManager}
 *
 * 应用提供器
 * 它的主要目的是扫描文件系统，并且找到可部署的上下文
 */
public interface AppProvider extends LifeCycle {
    /**
     * Set the Deployment Manager
     *
     * 设置部署管理器
     * 
     * @param deploymentManager the deployment manager
     * @throws IllegalStateException
     *             if the provider {@link #isRunning()}.
     */
    void setDeploymentManager(DeploymentManager deploymentManager);
    
    /* ------------------------------------------------------------ */
    /** Create a ContextHandler for an App
     *
     * 创建上下文处理器
     *
     * @param app The App
     * @return A ContextHandler
     * @throws IOException if unable to create context
     * @throws Exception if unable to create context
     */
    ContextHandler createContextHandler(App app) throws Exception;
}
