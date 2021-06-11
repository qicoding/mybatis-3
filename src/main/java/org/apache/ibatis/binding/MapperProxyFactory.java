/**
 *    Copyright 2009-2019 the original author or authors.
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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperProxy.MapperMethodInvoker;
import org.apache.ibatis.session.SqlSession;

/**
 * 映射代理器工厂类
 * @author Lasse Voss
 */
public class MapperProxyFactory<T> {

  /** 映射器接口类 */
  private final Class<T> mapperInterface;
  /** 方法缓存Map,映射接口类方法对象-映射方法类对象 */
  private final Map<Method, MapperMethodInvoker> methodCache = new ConcurrentHashMap<>();

  public MapperProxyFactory(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * 获取映射接口类
   * @return {@link #mapperInterface}
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * 方法缓存Map,映射接口类方法对象-映射方法类对象
   * @return {@link #mapperInterface}
   */
  public Map<Method, MapperMethodInvoker> getMethodCache() {
    return methodCache;
  }

  /**
   * 创建mapperInterface代理对象
   * @param mapperProxy 映射代理器对象
   * @return mapperInterface代理对象
   */
  @SuppressWarnings("unchecked")
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  /**
   * 创建mapperInterface代理对象
   * @param sqlSession 数据库对话对象
   * @return
   */
  public T newInstance(SqlSession sqlSession) {
    // 新建mapperInterface的 映射代理器对象
    final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
    // 创建mapperInterface代理对象
    return newInstance(mapperProxy);
  }

}
