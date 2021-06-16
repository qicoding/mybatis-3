/**
 *    Copyright 2009-2021 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ibatis.util.MapUtil;

/**
 * 默认反射器工厂
 */
public class DefaultReflectorFactory implements ReflectorFactory {
  /** 类缓存启用 */
  private boolean classCacheEnabled = true;
  /** 反射信息类对象 映射缓存集合 */
  private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

  public DefaultReflectorFactory() {
  }

  /**
   * 判断类缓存是否启用
   */
  @Override
  public boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }

  /**
   * 设置类缓存启用与否
   */
  @Override
  public void setClassCacheEnabled(boolean classCacheEnabled) {
    this.classCacheEnabled = classCacheEnabled;
  }

  /**
   * 获取 {@code type} 的反射信息类对象
   * @param type 类
   * @return
   */
  @Override
  public Reflector findForClass(Class<?> type) {
    // 如果启用了缓存
    if (classCacheEnabled) {
      // synchronized (type) removed see issue #461
      // 从reflectorMap获取type对应的Reflector，如果没有，就新建一个
      return MapUtil.computeIfAbsent(reflectorMap, type, Reflector::new);
    } else {
      return new Reflector(type);
    }
  }

}
