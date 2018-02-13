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


package org.eclipse.jetty.nosql.mongodb;

import java.net.UnknownHostException;

import org.eclipse.jetty.server.session.AbstractSessionDataStoreFactory;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.server.session.SessionDataStore;


import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.MongoURI;

/**
 * MongoSessionDataStoreFactory
 *
 * 基于Mongo的session数据保存工厂
 */
public class MongoSessionDataStoreFactory extends AbstractSessionDataStoreFactory {
    /**
     * 数据库名
     */
    String _dbName;

    /**
     * 集合名
     */
    String _collectionName;

    /**
     * 主机名
     */
    String _host;

    /**
     * 连接字符串
     * 它比直接设置主机名和端口有更高的优先级
     */
    String _connectionString;

    /**
     * 端口号
     */
    int _port = -1;

    /**
     * 获取主机信息
     *
     * @return the host
     */
    public String getHost() {
        return _host;
    }

    /**
     * 设置主机信息
     *
     * @param host the host to set
     */
    public void setHost(String host) {
        _host = host;
    }

    /**
     * 获取端口信息
     *
     * @return the port
     */
    public int getPort() {
        return _port;
    }

    /**
     * 设置端口信息
     *
     * @param port the port to set
     */
    public void setPort(int port) {
        _port = port;
    }

    /**
     * 获取数据库名
     *
     * @return the dbName
     */
    public String getDbName() {
        return _dbName;
    }

    /**
     * 设置数据库名
     *
     * @param dbName the dbName to set
     */
    public void setDbName(String dbName) {
        _dbName = dbName;
    }

    /**
     * 获取连接字符串
     *
     * @return the connectionString
     */
    public String getConnectionString() {
        return _connectionString;
    }

    /**
     * 设置连接字符串
     * 它比设置直接设置主机和端口有更高的优先级
     *
     * @param  connectionString the connection string to set. This has priority over dbHost and port
     */
    public void setConnectionString(String connectionString) {
        _connectionString = connectionString;
    }

    /**
     * 返回集合名称
     *
     * @return the collectionName
     */
    public String getCollectionName() {
        return _collectionName;
    }

    /**
     * 设置集合名称
     *
     * @param collectionName the collectionName to set
     */
    public void setCollectionName(String collectionName) {
        _collectionName = collectionName;
    }


    /**
     * 获取Session数据连接
     *
     * @throws MongoException
     * @throws UnknownHostException
     * @see org.eclipse.jetty.server.session.SessionDataStoreFactory#getSessionDataStore(org.eclipse.jetty.server.session.SessionHandler)
     */
    @Override
    public SessionDataStore getSessionDataStore(SessionHandler handler) throws Exception {
        MongoSessionDataStore store = new MongoSessionDataStore();
        store.setGracePeriodSec(getGracePeriodSec());
        store.setSavePeriodSec(getSavePeriodSec());
        Mongo mongo;

        if (!StringUtil.isBlank(getConnectionString())) {
            mongo = new Mongo(new MongoURI(getConnectionString()));
        } else if (!StringUtil.isBlank(getHost()) && getPort() != -1) {
            mongo = new Mongo(getHost(), getPort());
        } else if (!StringUtil.isBlank(getHost())) {
            mongo = new Mongo(getHost());
        } else {
            mongo = new Mongo();
        }
        store.setDBCollection(mongo.getDB(getDbName()).getCollection(getCollectionName()));
        return store;
    }

}
