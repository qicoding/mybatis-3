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
package org.apache.ibatis.reflection;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * 元对象
 * <p>
 *   只是对对象附加一下更加便捷描述的功能，Meta一般表示对一个东西的描述信息
 * </p>
 * @author Clinton Begin
 */
public class MetaObject {

  /** 元素对象，当前类对象 即原始对象 */
  private final Object originalObject;
  private final ObjectWrapper objectWrapper;
  private final ObjectFactory objectFactory;
  private final ObjectWrapperFactory objectWrapperFactory;
  private final ReflectorFactory reflectorFactory;

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;

    // 如果参数对象实现了ObjectWrapper
    if (object instanceof ObjectWrapper) {
      this.objectWrapper = (ObjectWrapper) object;
      // 如果objectWrapperFactory已经包装了对象，对用objectWrapperFactory的getWrapperFor
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
      // 是一个Map对象，使用mybatis的MapWrapper
    } else if (object instanceof Map) {
      this.objectWrapper = new MapWrapper(this, (Map) object);
      // 是一个CollectionWrapper对象
    } else if (object instanceof Collection) {
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
      // 其他默认使用BeanWrapper
    } else {
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }

  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

  /**
   * 判断PropertyTokenizer(参数name)是否还存在下一级，如果存在递归该方法，指定找到尾部，再获取其值。
   * 最后是通过 {@link ObjectWrapper#get(PropertyTokenizer)} 获得对应(参数name)的对象
   * <p>
   *     例：<br/>
   *     以(参数name='order[0].name')调用该方法，首先将参数封装成 {@link PropertyTokenizer} 并
   *     赋值到(变量prop,prop={name='order',index='0',children='name'})中,再调用
   *     {@link PropertyTokenizer#hasNext()} 得到true,再调用
   *     {@link PropertyTokenizer#getIndexedName()}得到'order[0]'，再用'order[0]'调用
   *     {@link MetaObject#metaObjectForProperty(String)}得到封装着 对应'order'的类中属性对象
   *     的{@link MetaObject} 并赋值给metaValue,然后判断metaValue是否等于{@link SystemMetaObject#NULL_META_OBJECT},
   *     是就返回null,如果不是，就调用 {@link PropertyTokenizer#getChildren()} 得到 'name',在用'name'作为
   *     参数递归调用 {@link MetaObject#getValue(String)}，这时以'name'为参数传进来，会将'name'封装成
   *     {@link PropertyTokenizer} 赋值给(变量prop,prop={name='name',index=null,children=null})，
   *     此时，{@link PropertyTokenizer#hasNext()}得到false，调用 {@link ObjectWrapper#get(PropertyTokenizer)}。
   * </p>
   */
  public Object getValue(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 存在下一级属性
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        return metaValue.getValue(prop.getChildren());
      }
    } else {
      return objectWrapper.get(prop);
    }
  }

  /**
   * 判断PropertyTokenizer(参数name)是否还存在下一级，如果存在递归该方法，指定找到最后一个对象，对其赋上(参数value)的值。
   * 最后是通过 {@link ObjectWrapper#set(PropertyTokenizer, Object)} 对对应于(参数name)的对象赋上(参数value)的值。
   * <p>
   *     例：<br/>
   *     以(参数name='order[0].name')调用该方法，首先将参数封装成 {@link PropertyTokenizer} 并
   *     赋值到(变量prop,prop={name='order',index='0',children='name'})中,再调用
   *     {@link PropertyTokenizer#hasNext()} 得到true,再调用
   *     {@link PropertyTokenizer#getIndexedName()}得到'order[0]'，再用'order[0]'调用
   *     {@link MetaObject#metaObjectForProperty(String)}得到封装着 对应'order'的类中属性对象
   *     的{@link MetaObject} 并赋值给metaValue,然后判断metaValue是否等于{@link SystemMetaObject#NULL_META_OBJECT},
   *     是就直接结束该方法,如果不是，调用
   * </p>
   */
  public void setValue(String name, Object value) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null) {
          // don't instantiate child path if value is null
          return;
        } else {
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      metaValue.setValue(prop.getChildren(), value);
    } else {
      objectWrapper.set(prop, value);
    }
  }

  /**
   * 获取封装 Object的参数name的属性对象 的 {@link MetaObject}
   * <p>
   *  以参数name调用 {@link MetaObject#getValue} 拿到Object中的(参数name)属性对象并赋值给变量value,
   *  然后将value封装成 {@link MetaObject} 对象返回出去
   * </p>
   */
  public MetaObject metaObjectForProperty(String name) {
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

  public void add(Object element) {
    objectWrapper.add(element);
  }

  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
