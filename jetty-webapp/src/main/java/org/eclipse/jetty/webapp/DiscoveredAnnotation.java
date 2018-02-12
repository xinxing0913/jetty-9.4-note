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

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;

/**
 * DiscoveredAnnotation
 *
 * Represents an annotation that has been discovered
 * by scanning source code of WEB-INF/classes and WEB-INF/lib jars.
 *
 * 通过注解发现
 * 通过扫描WEB-INF/classes或者WEB-INF/lib的jar包的代码获取
 */
public abstract class DiscoveredAnnotation {
    /**
     * 日志类
     */
    private static final Logger LOG = Log.getLogger(DiscoveredAnnotation.class);

    /**
     * 上下文
     */
    protected WebAppContext _context;

    /**
     * 类名
     */
    protected String _className;

    /**
     * 类
     */
    protected Class<?> _clazz;

    /**
     * resource it was discovered on, can be null (eg from WEB-INF/classes)
     *
     * 资源
     */
    protected Resource _resource;

    /**
     * 提供
     */
    public abstract void apply();

    /**
     * 构造方法
     *
     * @param context
     * @param className
     */
    public DiscoveredAnnotation (WebAppContext context, String className) {
        this(context,className, null);
    }

    /**
     * 构造方法
     *
     * @param context
     * @param className
     * @param resource
     */
    public DiscoveredAnnotation(WebAppContext context, String className, Resource resource) {
        _context = context;
        _className = className;
        _resource = resource;
    }

    /**
     * 获取资源
     *
     * @return
     */
    public Resource getResource () {
        return _resource;
    }

    /**
     * 获取目标类
     */
    public Class<?> getTargetClass() {
        if (_clazz != null) {
            return _clazz;
        }
        
        loadClass();
        
        return _clazz;
    }

    /**
     * 加载类
     */
    private void loadClass () {
        if (_clazz != null) {
            return;
        }
        
        if (_className == null)
            return;
        
        try {
            _clazz = Loader.loadClass(_className);
        } catch (Exception e) {
            LOG.warn(e);
        }
    }  
}
