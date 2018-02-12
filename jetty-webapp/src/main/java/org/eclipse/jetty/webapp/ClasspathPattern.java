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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.util.ArrayTernaryTrie;
import org.eclipse.jetty.util.IncludeExcludeSet;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;

/**
 * Classpath classes list performs pattern matching of a class name 
 * against an internal array of classpath pattern entries.
 * A class pattern is a string of one of the forms:<ul>
 * <li>'org.package.SomeClass' will match a specific class
 * <li>'org.package.' will match a specific package hierarchy
 * <li>'org.package.SomeClass$NestedClass ' will match a nested class exactly otherwise.
 * Nested classes are matched by their containing class. (eg. org.example.MyClass
 * matches org.example.MyClass$AnyNestedClass)
 * <li>'file:///some/location/' - A file system directory from which 
 * the class was loaded
 * <li>'file:///some/location.jar' - The URI of a jar file from which 
 * the class was loaded
 * <li>'jrt:/modulename' - A Java9 module name</li>
 * <li>Any of the above patterns preceeded by '-' will exclude rather than include the match.
 * </ul>
 * When class is initialized from a classpath pattern string, entries 
 * in this string should be separated by ':' (semicolon) or ',' (comma).
 *
 * 类路径模式
 * 在9.2的时候，这里做的还比较轻量级
 * 拥抱java9，所以这里添加了对模块的支持
 */
public class ClasspathPattern extends AbstractSet<String> {

    /**
     * 日志类
     */
    private static final Logger LOG = Log.getLogger(ClasspathPattern.class);

    /**
     * 单位
     */
    private static class Entry {
        /**
         * 模式
         */
        private final String _pattern;
        /**
         * 名称
         */
        private final String _name;

        /**
         * 是包含还是排除
         */
        private final boolean _inclusive;

        /**
         * 构造方法
         *
         * @param name
         * @param inclusive
         */
        protected Entry(String name, boolean inclusive) {
            _name = name;
            _inclusive = inclusive;
            _pattern = inclusive ? _name : ("-"+_name);
        }

        /**
         * 获取模式
         *
         * @return
         */
        public String getPattern() {
            return _pattern;
        }

        /**
         * 获取名称
         *
         * @return
         */
        public String getName() {
            return _name;
        }

        /**
         * 转换为字符串
         *
         * @return
         */
        @Override
        public String toString() {
            return _pattern;
        }

        /**
         * 哈希值
         *
         * @return
         */
        @Override 
        public int hashCode() {
            return _pattern.hashCode();
        }

        /**
         * 判断是否相等
         *
         * @param o
         * @return
         */
        @Override 
        public boolean equals(Object o) {
            return (o instanceof Entry) && _pattern.equals(((Entry)o)._pattern);
        }

        /**
         * 是否是包含
         *
         * @return
         */
        public boolean isInclusive() {
            return _inclusive;
        }
    }

    /**
     * 包记录
     */
    private static class PackageEntry extends Entry {
        /**
         * 构造方法
         *
         * @param name
         * @param inclusive
         */
        protected PackageEntry(String name, boolean inclusive) {
            super(name, inclusive);
        }
    }

    /**
     * 类记录
     */
    private static class ClassEntry extends Entry {
        /**
         * 构造方法
         *
         * @param name
         * @param inclusive
         */
        protected ClassEntry(String name, boolean inclusive) {
            super(name, inclusive);
        }
    }

    /**
     * 位置记录
     */
    private static class LocationEntry extends Entry {
        /**
         * 文件
         */
        private final File _file;

        /**
         * 构造方法
         *
         * @param name
         * @param inclusive
         */
        protected LocationEntry(String name, boolean inclusive) {
            super(name, inclusive);
            if (!getName().startsWith("file:"))
                throw new IllegalArgumentException(name);
            try {
                _file = Resource.newResource(getName()).getFile();
            } catch(IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        /**
         * 获取文件
         *
         * @return
         */
        public File getFile() {
            return _file;
        }
    }

    /**
     * 模块记录
     */
    private static class ModuleEntry extends Entry {

        /**
         * 模块名
         */
        private final String _module;

        /**
         * 构造方法
         *
         * @param name
         * @param inclusive
         */
        protected ModuleEntry(String name, boolean inclusive) {
            super(name, inclusive);
            if (!getName().startsWith("jrt:")) {
                throw new IllegalArgumentException(name);
            }
            _module = getName().split("/")[1];
        }

        /**
         * 获取模块名
         *
         * @return
         */
        public String getModule() {
            return _module;
        }
    }

    /**
     * 通过包
     */
    public static class ByPackage extends AbstractSet<Entry> implements Predicate<String> {

        /**
         * 记录
         */
        private final ArrayTernaryTrie.Growing<Entry> _entries = new ArrayTernaryTrie.Growing<>(false,512,512);

        /**
         * 测试
         *
         * @param name
         * @return
         */
        @Override
        public boolean test(String name) {
            return _entries.getBest(name)!=null;
        }

        /**
         * 获取迭代器
         *
         * @return
         */
        @Override
        public Iterator<Entry> iterator() {
            return _entries.keySet().stream().map(_entries::get).iterator();
        }

        /**
         * 大小
         *
         * @return
         */
        @Override
        public int size() {
            return _entries.size();
        }

        /**
         * 是否为空
         *
         * @return
         */
        @Override
        public boolean isEmpty() {
            return _entries.isEmpty();
        }

        /**
         * 添加
         *
         * @param entry
         * @return
         */
        @Override
        public boolean add(Entry entry) {
            String name = entry.getName();
            if (entry instanceof ClassEntry) {
                name += "$";
            } else if (!(entry instanceof PackageEntry)) {
                throw new IllegalArgumentException(entry.toString());
            } else if (".".equals(name)) {
                name ="";
            }
                
            if (_entries.get(name) != null) {
                return false;
            }
            
            return _entries.put(name,entry);
        }

        /**
         * 移除
         */
        @Override
        public boolean remove(Object entry) {
            if (!(entry instanceof Entry))
                return false;

            return _entries.remove(((Entry)entry).getName())!=null;
        }

        /**
         * 清空
         */
        @Override
        public void clear() {
            _entries.clear();
        }
    }

    /**
     * 通过类
     */
    @SuppressWarnings("serial")
    public static class ByClass extends HashSet<Entry> implements Predicate<String> {
        /**
         * 所有的记录
         */
        private final Map<String,Entry> _entries = new HashMap<>();

        /**
         * 测试
         *
         * @param name
         * @return
         */
        @Override
        public boolean test(String name) {
            return _entries.containsKey(name);
        }

        /**
         * 获取迭代器
         *
         * @return
         */
        @Override
        public Iterator<Entry> iterator() {
            return _entries.values().iterator();
        }

        /**
         * 大小
         *
         * @return
         */
        @Override
        public int size() {
            return _entries.size();
        }

        /**
         * 添加，需要注意类型
         *
         * @param entry
         * @return
         */
        @Override
        public boolean add(Entry entry) {
            if (!(entry instanceof ClassEntry)) {
                throw new IllegalArgumentException(entry.toString());
            }
            return _entries.put(entry.getName(),entry)==null;
        }

        /**
         * 移除
         *
         * @param entry
         * @return
         */
        @Override
        public boolean remove(Object entry) {
            if (!(entry instanceof Entry)) {
                return false;
            }

            return _entries.remove(((Entry)entry).getName())!=null;
        }
    }

    /**
     * 通过包或者类名
     */
    public static class ByPackageOrName extends AbstractSet<Entry> implements Predicate<String> {
        /**
         * 通过类的实例
         */
        private final ByClass _byClass = new ByClass();

        /**
         * 通过包的实例
         */
        private final ByPackage _byPackage = new ByPackage();

        /**
         * 检测
         *
         * @param name
         * @return
         */
        @Override
        public boolean test(String name) {
            return _byPackage.test(name) || _byClass.test(name) ;
        }

        /**
         * 通过包的方式会包含所有的记录
         * 类时后缀为$的包
         *
         * @return
         */
        @Override
        public Iterator<Entry> iterator() {
            // by package contains all entries (classes are also $ packages).
            return _byPackage.iterator();
        }

        /**
         * 大小
         *
         * @return
         */
        @Override
        public int size()
        {
            return _byPackage.size();
        }

        /**
         * 添加
         *
         * @param entry
         * @return
         */
        @Override
        public boolean add(Entry entry) {
            if (entry instanceof PackageEntry) {
                return _byPackage.add(entry);
            }

            if (entry instanceof ClassEntry) {
                // Add class name to packages also as classes act
                // as packages for nested classes.
                boolean added = _byPackage.add(entry);
                added = _byClass.add(entry) || added;
                return added;
            }

            throw new IllegalArgumentException();
        }

        /**
         * 移除
         *
         * @param o
         * @return
         */
        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }

            boolean removedPackage = _byPackage.remove(o);
            boolean removedClass = _byClass.remove(o);

            return removedPackage || removedClass;
        }

        /**
         * 清空
         */
        @Override
        public void clear() {
            _byPackage.clear();
            _byClass.clear();
        }
    }

    /**
     * 通过位置信息来获取
     */
    @SuppressWarnings("serial")
    public static class ByLocation extends HashSet<Entry> implements Predicate<URI> {

        /**
         * 测试是否存在
         *
         * @param uri
         * @return
         */
        @Override
        public boolean test(URI uri) {
            if (!uri.getScheme().equals("file")) {
                return false;
            }
            Path path = Paths.get(uri);

            for (Entry entry : this) {
                if (!(entry instanceof LocationEntry)) {
                    throw new IllegalStateException();
                }

                File file = ((LocationEntry)entry).getFile();

                if (file.isDirectory()) {
                    if (path.startsWith(file.toPath())) {
                        return true;
                    }
                } else {
                    if (path.equals(file.toPath())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * 通过模块
     */
    @SuppressWarnings("serial")
    public static class ByModule extends HashSet<Entry> implements Predicate<URI> {
        /**
         * 记录所有的内容
         */
        private final ArrayTernaryTrie.Growing<Entry> _entries = new ArrayTernaryTrie.Growing<>(false,512,512);

        /**
         * 测试
         *
         * @param uri
         * @return
         */
        @Override
        public boolean test(URI uri) {
            if (!uri.getScheme().equalsIgnoreCase("jrt")) {
                return false;
            }
            String module = uri.getPath();
            int end = module.indexOf('/',1);
            if (end<1) {
                end = module.length();
            }
            return _entries.get(module,1,end-1)!=null;
        }

        /**
         * 获取迭代器
         *
         * @return
         */
        @Override
        public Iterator<Entry> iterator() {
            return _entries.keySet().stream().map(_entries::get).iterator();
        }

        /**
         * 获取大小
         *
         * @return
         */
        @Override
        public int size() {
            return _entries.size();
        }

        /**
         * 添加
         *
         * @param entry
         * @return
         */
        @Override
        public boolean add(Entry entry) {
            if (!(entry instanceof ModuleEntry)) {
                throw new IllegalArgumentException(entry.toString());
            }
            String module = ((ModuleEntry)entry).getModule();

            if (_entries.get(module)!=null) {
                return false;
            }
            _entries.put(module,entry);
            return true;
        }

        /**
         * 移除
         *
         * @param entry
         * @return
         */
        @Override
        public boolean remove(Object entry) {
            if (!(entry instanceof Entry)) {
                return false;
            }

            return _entries.remove(((Entry)entry).getName())!=null;
        }
    }


    /**
     * 通过位置或者模块
     */
    public static class ByLocationOrModule extends AbstractSet<Entry> implements Predicate<URI> {
        /**
         * 通过位置
         */
        private final ByLocation _byLocation = new ByLocation();

        /**
         * 通过模块
         */
        private final ByModule _byModule = new ByModule();

        /**
         * 测试
         *
         * @param name
         * @return
         */
        @Override
        public boolean test(URI name) {
            return _byLocation.test(name) || _byModule.test(name);
        }

        /**
         * 获取迭代器
         *
         * @return
         */
        @Override
        public Iterator<Entry> iterator() {
            Set<Entry> entries = new HashSet<>();
            entries.addAll(_byLocation);
            entries.addAll(_byModule);
            return entries.iterator();
        }

        /**
         * 大小
         *
         * @return
         */
        @Override
        public int size() {
            return _byLocation.size()+_byModule.size();
        }

        /**
         * 添加
         *
         * @param entry
         * @return
         */
        @Override
        public boolean add(Entry entry) {
            if (entry instanceof LocationEntry) {
                return _byLocation.add(entry);
            }
            if (entry instanceof ModuleEntry) {
                return _byModule.add(entry);
            }

            throw new IllegalArgumentException(entry.toString());
        }

        /**
         * 移除
         *
         * @param o
         * @return
         */
        @Override
        public boolean remove(Object o) {
            if (o instanceof LocationEntry) {
                return _byLocation.remove(o);
            }
            if (o instanceof ModuleEntry) {
                return _byModule.remove(o);
            }
            return false;
        }

        /**
         * 清空
         */
        @Override
        public void clear() {
            _byLocation.clear();
            _byModule.clear();
        }
    }

    /**
     * 所有的记录
     */
    Map<String,Entry> _entries = new HashMap<>();

    /**
     * 模式
     */
    IncludeExcludeSet<Entry,String> _patterns = new IncludeExcludeSet<>(ByPackageOrName.class);

    /**
     * 位置
     */
    IncludeExcludeSet<Entry,URI> _locations = new IncludeExcludeSet<>(ByLocationOrModule.class);

    /**
     * 构造方法
     */
    public ClasspathPattern() {
    }

    /**
     * 构造方法
     *
     * @param patterns
     */
    public ClasspathPattern(String[] patterns) {
        setAll(patterns);
    }

    /**
     * 构造方法
     *
     * @param pattern
     */
    public ClasspathPattern(String pattern) {
        add(pattern);
    }

    /**
     * 包含
     *
     * @param name
     * @return
     */
    public boolean include(String name) {
        if (name == null) {
            return false;
        }
        return add(newEntry(name,true));
    }

    /**
     * 包含
     *
     * @param name
     * @return
     */
    public boolean include(String... name) {
        boolean added = false;
        for (String n:name) {
            if (n!=null) {
                added = add(newEntry(n,true)) || added;
            }
        }
        return added;
    }

    /**
     * 移除
     *
     * @param name
     * @return
     */
    public boolean exclude(String name) {
        if (name==null) {
            return false;
        }
        return add(newEntry(name,false));
    }

    /**
     * 移除
     *
     * @param name
     * @return
     */
    public boolean exclude(String... name) {
        boolean added = false;
        for (String n:name) {
            if (n!=null) {
                added = add(newEntry(n,false)) || added;
            }
        }
        return added;
    }

    /**
     * 添加
     *
     * @param pattern
     * @return
     */
    @Override
    public boolean add(String pattern) {
        if (pattern==null) {
            return false;
        }
        return add(newEntry(pattern));
    }

    /**
     * 添加
     *
     * @param pattern
     * @return
     */
    public boolean add(String... pattern) {
        boolean added = false;
        for (String p:pattern)
            if (p!=null)
                added = add(newEntry(p)) || added;
        return added;
    }

    /**
     * 新建一条记录
     *
     * @param pattern
     * @return
     */
    protected Entry newEntry(String pattern) {
        if (pattern.startsWith("-")) {
            return newEntry(pattern.substring(1),false);
        }
        return newEntry(pattern,true);
    }

    /**
     * 新建一条记录
     *
     * @param name
     * @param inclusive
     * @return
     */
    protected Entry newEntry(String name, boolean inclusive) {
        if (name.startsWith("-")) {
            throw new IllegalStateException(name);
        }
        if (name.startsWith("file:")) {
            return new LocationEntry(name, inclusive);
        }
        if (name.startsWith("jrt:")) {
            return new ModuleEntry(name, inclusive);
        }
        if (name.endsWith(".")) {
            return new PackageEntry(name, inclusive);
        }
        return new ClassEntry(name,inclusive);
    }

    /**
     * 添加实体
     *
     * @param entry
     * @return
     */
    protected boolean add(Entry entry) {
        if (_entries.containsKey(entry.getPattern())) {
            return false;
        }
        _entries.put(entry.getPattern(),entry);

        if (entry instanceof LocationEntry || entry instanceof ModuleEntry) {
            if (entry.isInclusive()) {
                _locations.include(entry);
            } else {
                _locations.exclude(entry);
            }
        } else {
            if (entry.isInclusive()) {
                _patterns.include(entry);
            } else {
                _patterns.exclude(entry);
            }
        }
        return true;
    }

    /**
     * 移除对象
     *
     * @param o
     * @return
     */
    @Override
    public boolean remove(Object o) {
        if (!(o instanceof String)) {
            return false;
        }

        String pattern = (String)o;

        Entry entry = _entries.remove(pattern);
        if (entry==null) {
            return false;
        }

        List<Entry> saved = new ArrayList<>(_entries.values());
        clear();
        for (Entry e:saved) {
            add(e);
        }
        return true;
    }

    /**
     * 清空
     */
    @Override
    public void clear() {
        _entries.clear();
        _patterns.clear();
        _locations.clear();
    }

    /**
     * 获取可迭代的字符串
     *
     * @return
     */
    @Override
    public Iterator<String> iterator() {
        return _entries.keySet().iterator();
    }

    /**
     * 获取当前大小
     *
     * @return
     */
    @Override
    public int size() {
        return _entries.size();
    }

    /**
     * Initialize the matcher by parsing each classpath pattern in an array
     *
     * 设置类数组
     *
     * @param classes array of classpath patterns
     */
    private void setAll(String[] classes) {
        _entries.clear();
        addAll(classes);
    }
    
    /**
     * 添加类数组
     *
     * @param classes array of classpath patterns
     */
    private void addAll(String[] classes) {
        if (classes!=null) {
            addAll(Arrays.asList(classes));
        }
    }
    
    /**
     * 获取所有的模式
     *
     * @return array of classpath patterns
     */
    public String[] getPatterns() {
        return toArray(new String[_entries.size()]);
    }

    
    /**
     * Match the class name against the pattern
     *
     * 判断是否匹配
     *
     * @param name name of the class to match
     * @return true if class matches the pattern
     */
    public boolean match(String name) {
        return _patterns.test(name);
    }
    
    /**
     * Match the class name against the pattern
     *
     * @param clazz A class to try to match
     * @return true if class matches the pattern
     */
    public boolean match(Class<?> clazz) {
        try {
            Boolean byName = _patterns.isIncludedAndNotExcluded(clazz.getName());
            if (Boolean.FALSE.equals(byName)) {
                return byName; // Already excluded so no need to check location.
            }
            URI location = TypeUtil.getLocationOfClass(clazz);
            Boolean byLocation = location == null ? null : _locations.isIncludedAndNotExcluded(location);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("match {} from {} byName={} byLocation={} in {}",clazz,location,byName,byLocation,this);
            }
            
            // Combine the tri-state match of both IncludeExclude Sets
            boolean included = byName==Boolean.TRUE || byLocation==Boolean.TRUE
                || (byName==null && !_patterns.hasIncludes() && byLocation==null && !_locations.hasIncludes());
            boolean excluded = byName==Boolean.FALSE || byLocation==Boolean.FALSE;
            return included && !excluded;
        } catch (Exception e) {
            LOG.warn(e);
        }
        return false;
    }

    /**
     * 是否匹配
     *
     * @param name
     * @param url
     * @return
     */
    public boolean match(String name, URL url) {
        // Strip class suffix for name matching
        if (name.endsWith(".class")) {
            name=name.substring(0,name.length()-6);
        }
        
        // Treat path elements as packages for name matching
        name = name.replace("/",".");

        Boolean byName = _patterns.isIncludedAndNotExcluded(name);
        if (Boolean.FALSE.equals(byName))
            return byName; // Already excluded so no need to check location.
        
        // Try to find a file path for location matching
        Boolean byLocation = null;
        try {
            URI jarUri = URIUtil.getJarSource(url.toURI());
            if ("file".equalsIgnoreCase(jarUri.getScheme())) {
                byLocation = _locations.isIncludedAndNotExcluded(jarUri);
            }
        } catch(Exception e) {
            LOG.ignore(e);
        }

        // Combine the tri-state match of both IncludeExclude Sets
        boolean included = byName==Boolean.TRUE || byLocation==Boolean.TRUE
            || (byName==null && !_patterns.hasIncludes() && byLocation==null && !_locations.hasIncludes());
        boolean excluded = byName==Boolean.FALSE || byLocation==Boolean.FALSE;
        return included && !excluded;
    }
    
}
