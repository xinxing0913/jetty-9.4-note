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

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;


/**
 * A WebAppClassLoader that caches {@link #getResource(String)} results.
 * Specifically this ClassLoader caches not found classes and resources,
 * which can greatly increase performance for applications that search 
 * for resources.
 *
 * 基于缓存的类加载器
 * 它是把找到的和找不到的都存起来，这样下次再寻找的时候可以直接使用
 * 但是也要注意实际场景，可能会导致一些bug
 * 如果场景正确的话，它可以大幅度提升性能
 */
@ManagedObject
public class CachingWebAppClassLoader extends WebAppClassLoader {
    /**
     * 日志类
     */
    private static final Logger LOG = Log.getLogger(CachingWebAppClassLoader.class);

    /**
     * 未找到的列表
     */
    private final Set<String> _notFound = ConcurrentHashMap.newKeySet();

    /**
     * 缓存
     */
    private final ConcurrentHashMap<String,URL> _cache = new ConcurrentHashMap<>();

    /**
     * 构造方法
     *
     * @param parent
     * @param context
     * @throws IOException
     */
    public CachingWebAppClassLoader(ClassLoader parent, Context context) throws IOException {
        super(parent,context);
    }

    /**
     * 构造方法
     *
     * @param context
     * @throws IOException
     */
    public CachingWebAppClassLoader(Context context) throws IOException {
        super(context);
    }

    /**
     * 获取资源
     *
     * @param name
     * @return
     */
    @Override
    public URL getResource(String name) {
        if (_notFound.contains(name)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not found cache hit resource {}",name);
            }
            return null;
        }
        
        URL url = _cache.get(name);
        
        if (name==null) {
            url = super.getResource(name);
        
            if (url==null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caching not found resource {}",name);
                }
                _notFound.add(name);
            } else {
                _cache.putIfAbsent(name,url);
            }
        }
        
        return url;
    }

    /**
     * 加载类
     *
     * @param name
     * @return
     * @throws ClassNotFoundException
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (_notFound.contains(name)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not found cache hit resource {}",name);
            }
            throw new ClassNotFoundException(name+": in notfound cache");
        }
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException nfe) {
            if (_notFound.add(name)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caching not found {}",name);
                    LOG.debug(nfe);
                }
            }
            throw nfe; 
        }
    }

    /**
     * 清空缓存
     */
    @ManagedOperation
    public void clearCache() {
        _cache.clear();
        _notFound.clear();
    }

    /**
     * 转换为字符串
     *
     * @return
     */
    @Override
    public String toString() {
        return "Caching["+super.toString()+"]";
    }
}
