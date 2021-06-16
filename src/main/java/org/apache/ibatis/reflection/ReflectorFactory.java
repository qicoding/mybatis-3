/**
 *    Copyright 2009-2015 the original author or authors.
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

/**
 * 反射信息工厂接口
 */
public interface ReflectorFactory {

  /**
   * 是否会缓存Reflector对象
   */
  boolean isClassCacheEnabled();

  /**
   * 设置是否缓存Reflector对象
   */
  void setClassCacheEnabled(boolean classCacheEnabled);

  /**
   * 查找type对应的Refector，没有则new一个Refector并设置进去缓存Map中。
   */
  Reflector findForClass(Class<?> type);
}
