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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装接口
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 获取原始对象
   * @param prop 字符串表达式解析封装类
   * @return
   */
  Object get(PropertyTokenizer prop);

  /**
   * 设置原始对象
   * @param prop 字符串表达式解析封装类
   * @param value 原始对象
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 在原始对象中查询属性
   * @param name
   * @param useCamelCaseMapping
   * @return
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 获取所有有getter的属性名
   * @return
   */
  String[] getGetterNames();

  /**
   * 获取所有有setter的属性名
   * @return
   */
  String[] getSetterNames();

  /**
   * 获取对应(参数name)类中属性的setter方法的参数类型
   * @param name
   * @return
   */
  Class<?> getSetterType(String name);

  /**
   * 获取对应(参数name)类中属性的getter方法的参数类型
   * @param name
   * @return
   */
  Class<?> getGetterType(String name);

  /**
   * 判断是否存在某属性的setter方法
   * @param name
   * @return
   */
  boolean hasSetter(String name);

  /**
   * 判断是否存在某属性的getter方法
   * @param name
   * @return
   */
  boolean hasGetter(String name);

  /**
   * 实例化属性值
   * @param name
   * @param prop
   * @param objectFactory
   * @return
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /**
   * 是否集合
   * @return
   */
  boolean isCollection();

  /**
   * 追加元素
   * @param element
   */
  void add(Object element);

  /**
   * 追加批量元素
   * @param element
   * @param <E>
   */
  <E> void addAll(List<E> element);

}
