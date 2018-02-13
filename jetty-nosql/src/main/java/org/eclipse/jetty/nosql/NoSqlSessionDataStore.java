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


package org.eclipse.jetty.nosql;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jetty.server.session.AbstractSessionDataStore;
import org.eclipse.jetty.server.session.SessionData;


/**
 * NoSqlSessionDataStore
 *
 * 使用nosql来存储session的基类
 */
public abstract class NoSqlSessionDataStore extends AbstractSessionDataStore {

    /**
     * 基于nosql存储的session数据
     */
    public class NoSqlSessionData extends SessionData {
        /**
         * 版本
         */
        private Object _version;

        /**
         * 脏属性
         */
        private Set<String> _dirtyAttributes = new HashSet<String>();

        /**
         * 构造方法
         *
         * @param id
         * @param cpath
         * @param vhost
         * @param created
         * @param accessed
         * @param lastAccessed
         * @param maxInactiveMs
         */
        public NoSqlSessionData(String id, String cpath, String vhost, long created, long accessed, long lastAccessed, long maxInactiveMs) {
            super(id, cpath, vhost, created, accessed, lastAccessed, maxInactiveMs);
        }

        /**
         * 设置版本
         *
         * @param v
         */
        public void setVersion (Object v) {
            _version = v;
        }

        /**
         * 获取版本
         *
         * @return
         */
        public Object getVersion () {
            return _version;
        }

        /**
         * 标记为脏
         *
         * @param name
         */
        @Override
        public void setDirty(String name) {
            super.setDirty(name);
            _dirtyAttributes.add(name);
        }

        /**
         * 获取所有的脏属性
         *
         * @return
         */
        public Set<String> takeDirtyAttributes() {
            Set<String> copy = new HashSet<>(_dirtyAttributes);
            _dirtyAttributes.clear();
            return copy;
        }

        /**
         * 获取所有的属性名
         *
         * @return
         */
        public Set<String> getAllAttributeNames () {
            return new HashSet<String>(_attributes.keySet());
        }
    }

    /**
     * 获取一个新的SessionData
     *
     * @param id
     * @param created
     * @param accessed
     * @param lastAccessed
     * @param maxInactiveMs
     * @return
     */
    @Override
    public SessionData newSessionData(String id, long created, long accessed, long lastAccessed, long maxInactiveMs) {
        return new NoSqlSessionData(id, _context.getCanonicalContextPath(), _context.getVhost(), created, accessed, lastAccessed, maxInactiveMs);
    }

}
