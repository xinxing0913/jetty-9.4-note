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
 * 克隆配置
 */
public class CloneConfiguration extends AbstractConfiguration {

    /**
     * 模板配置
     */
    final WebAppContext _template;

    /**
     * 构造方法
     *
     * @param template
     */
    CloneConfiguration(WebAppContext template) {
        _template=template;
    }

    /**
     * 配置
     *
     * @param context The context to configure
     * @throws Exception
     */
    @Override
    public void configure(WebAppContext context) throws Exception {
        for (Configuration configuration : _template.getConfigurations()) {
            configuration.cloneConfigure(_template,context);
        }
    }

    /**
     * 清空配置
     *
     * @param context The context to configure
     * @throws Exception
     */
    @Override
    public void deconfigure(WebAppContext context) throws Exception {
        for (Configuration configuration : _template.getConfigurations()) {
            configuration.deconfigure(context);
        }
    }
}
