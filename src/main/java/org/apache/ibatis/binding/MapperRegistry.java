/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * 映射器注册器
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {

  /** Mybatis全局配置信息 */
  private final Configuration config;
  /** 已注册的映射器接口类映射，key=>映射器接口类，value=>映射代理器工厂 */
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

  /**
   * @param config Mybatis全局配置信息
   */
  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  /**
   * 获取映射器
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    // 获取type对应的映射代理器工厂
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    // 如果映射代理器工厂为null
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      // 创建type代理对象
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  /**
   * 判断是否已经进行注册该类
   * <p>
   *    从{@link #knownMappers}进行判断。
   * </p>
   * @param type Mapper.xml对应的接口类
   * @return 如果{@code type} 存在knownMappers中，返回true;否则返回false
   */
  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * 将 Mapper.xml对应的接口类 加入到knownMapper中
   * @param type Mapper.xml对应的接口类
   */
  public <T> void addMapper(Class<T> type) {
    // 只有接口才会进行注册
    if (type.isInterface()) {
      // 如果在knownMappers已经找到了对应的type，将抛出异常
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      // 已加载完成标记
      boolean loadCompleted = false;
      try {
        // 将type和对应的映射代理器工厂添加到knowMappers中
        knownMappers.put(type, new MapperProxyFactory<>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        /*
         * 在解析器运行之前添加 type 到 knownMappers 很重要，
         * 否则映射器解析器可能会自动尝试绑定。
         * 如果类型已知，则不会尝试。
         */
        // 新建一个 映射器注解构建器
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        /*
         * 解析映射接口类，解析mapper标签下的所有方法和注解，并对解析出来的信息加以封装，
         * 然后添加到Mybatis全局配置信息中。然后重新解析Mybatis全局配置信息中未能完成解析的Method重新解析
         */
        parser.parse();
        // 设置已加载完成标记为true
        loadCompleted = true;
      } finally {
        // 如果未能完成加载的接口映射类
        if (!loadCompleted) {
          // 从knownMappers中移除接口映射类
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * 获取已注册的映射接口类集合
   * Gets the mappers.
   *
   * @return the mappers
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * 添加映射器
   * Adds the mappers.
   *
   * @param packageName
   *          the package name
   * @param superType
   *          the super type
   * @since 3.2.2
   */
  public void addMappers(String packageName, Class<?> superType) {
    // ResolverUtil:用于查找在类路径可用并满足任意条件的类。
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    // 查找在packageName下的superType的子类
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    // 获取匹配的类集合
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    // 遍历映射类集合
    for (Class<?> mapperClass : mapperSet) {
      // 将 映射接口类 加入到knownMappper中
      addMapper(mapperClass);
    }
  }

  /**
   * 添加映射器
   * Adds the mappers.
   *
   * @param packageName
   *          the package name
   * @since 3.2.2
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

}
