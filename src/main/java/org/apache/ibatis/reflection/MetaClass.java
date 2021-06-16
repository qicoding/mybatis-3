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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 元类
 * <p>
 *    MetaClass是mybatis用于简化反射操作的封装类。只是对类附加一下更加便捷描述的功能，Meta一般表示对一个东西的描述信息
 * </p>
 * @author Clinton Begin
 */
public class MetaClass {

  /** 当前类的 {@link ReflectorFactory} */
  private final ReflectorFactory reflectorFactory;
  /** 当前类的 {@link Reflector} */
  private final Reflector reflector;

  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 在reflector中取出参数name的getter方法的返回类型propType，再将propType封装成MetaClass返回出去（装饰者模式）
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 获取属性,以参数name='Order[0].Name'调用该方法，会从先查找类中是否有'Order'对应的属性order,
   * 找到之后，再找order属性getter方法的返回类型封装成MetaClass，在查找看看有没有'Name'对应的name属性，
   * 最后该方法返回'order.name'
   */
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 查找属性,useCamelCaseMapping=true时，假设name='O_r_der'时，就会变成'order',
   * 最后还是调用{@link MetaClass#findProperty(String)}
   * @param useCamelCaseMapping 使用驼峰式大小写映射，会将name中的下划线字符全部改成空字符串
   * @return
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  /**
   *
   * 实际直接调用 {@link Reflector#getGetablePropertyNames()}
   * @return
   */
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  /**
   * 实际直接调用 {@link Reflector#getSetablePropertyNames()}
   * @return
   */
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  /**
   * 返回对应(参数name)类中属性的setter方法的参数类型，最终调用 {@link Reflector#getSetterType(String)}获取
   * <p>
   *     以参数名name='order[0].name'为例执行该方法：将name封装PropertyTokenizer(name='order',index='0',childer='name')
   *     并赋值给prop变量,调用(变量prop)的 {@link PropertyTokenizer#hasNext()} 得到true,将 从(变量prop)中调用 {@link PropertyTokenizer#getName()}
   *     得到'order' 传入 {@link MetaClass#metaClassForProperty(String)}得到对应'order'的类中属性的 {@link MetaClass} 并
   *     赋值给(变量metaProp),再将 从(变量prop)调用{@link PropertyTokenizer#getChildren()}得到'name' 传入
   *     (变量metaProp)的 {@link MetaClass#getSetterType(String)}:将'name' 封装成 {@link PropertyTokenizer} 并赋值给
   *     (变量prop,prop={name='name',index=null,children=null})，调用(变量prop)的 {@link PropertyTokenizer#hasNext()}
   *     得到false，最终调用 {@link Reflector#getSetterType(String)} 获取对应'name'的类中属性的setter的参数类型
   * </p>
   *
   */
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 返回对应(参数name)类中属性的getter方法的参数类型，最终调用 {@link Reflector#getGetterType(String)}}获取
   * <p>
   *     以参数名name='order[0].name'为例执行该方法：将name封装PropertyTokenizer(name='order',index='0',childer='name')
   *     并赋值给prop变量,调用(变量prop)的 {@link PropertyTokenizer#hasNext()} 得到true,将 从(变量prop)中调用 {@link PropertyTokenizer#getName()}
   *     得到'order' 传入 {@link MetaClass#metaClassForProperty(String)}得到对应'order'的类中属性的 {@link MetaClass} 并
   *     赋值给(变量metaProp),再将 从(变量prop)调用{@link PropertyTokenizer#getChildren()}得到'name' 传入
   *     (变量metaProp)的 {@link MetaClass#getGetterType(String)}:将'name' 封装成 {@link PropertyTokenizer} 并赋值给
   *     (变量prop,prop={name='name',index=null,children=null})，调用(变量prop)的 {@link PropertyTokenizer#hasNext()}
   *     得到false，最终调用 {@link Reflector#getGetterType(String)} 获取对应'name'的类中属性的setter的参数类型
   * </p>
   *
   */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  /**
   * 获取对应(参数prop)的属性的 {@link MetaClass}
   * <p>
   *     调用 {@link MetaClass#getGetterType(PropertyTokenizer)} 得到 对应(参数prop)的属性的类型并赋值给
   *     (变量propType)，再将(变量propType)和(成员变量reflectorFactory)传入 {@link MetaClass#forClass(Class, ReflectorFactory)}
   *     得到 {@link MetaClass} 返回出去
   * </p>
   */
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 获取参数prop的getter方法的返回类型:
   * <p>
   *     例:<br/>
   *     以'order[0].name'作为参数prop调用该方法，(p:prop={name='order',index='0',children='name'}),
   *     以prop.getName()='order'调用 {@link Reflector#getGetterType(String)} 得到类中'order'属性的
   *     getter方法返回类型赋值给type。在判断prop.index是否有值以及type是否属于集合类型。如果条件满足，将
   *     prop.getName()='order'作为参数调用 {@link MetaClass#getGenericGetterType(String)} 获取属性
   *     getter方法的返回类型，并赋值给returnType变量。假设，returnType是一个泛型ParameterizeType，就会
   *     通过 {@link ParameterizedType#getActualTypeArguments()} 得到声明的类型，并赋值给actualTypeArguments
   *     变量，判断actualTypeArgument的数组长度是否只有一个（方法的返回类型肯定是只有一个，所以一定为true），再取出
   *     actualTypeArguments的第一个元素并赋值给returnType变量，再判断returnType是泛型还是普通java类，如果是普通
   *     java类型，就直接返回出去，如果是泛型，就返回泛型中<>前面的那个类
   * </p>
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    Class<?> type = reflector.getGetterType(prop.getName());
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      Type returnType = getGenericGetterType(prop.getName());
      if (returnType instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  /**
   * 获取通用Getter方法返回类型，捕捉了反射的相关异常，找不到propertyName的执行器就会返回null
   */
  private Type getGenericGetterType(String propertyName) {
    try {
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        declaredMethod.setAccessible(true);
        Method method = (Method) declaredMethod.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        Field field = (Field) declaredField.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  /**
   * 判断是否存在对应(参数name)的属性的setter方法,找出对应(参数name)的属性，最终调用
   * {@link Reflector#hasSetter(String)} 判断。
   * <p>
   *    将(参数name='order[0].name')传入该方法，将(参数name)包装成 {@link PropertyTokenizer} 并
   *    赋值给(变量prop，prop={name='order',index='0',children='name'),调用（变量prop)的
   *    {@link PropertyTokenizer#hasNext()}得到true,将 从(变量prop)的调用{@link PropertyTokenizer#getName()}
   *    得到'order'传入{@link Reflector#hasSetter(String)}得到true,再将 从(变量prop)的调用 {@link PropertyTokenizer#getName()}
   *    得到'order' 调用 {@link MetaClass#metaClassForProperty(String)}得到对应'order'的属性的 {@link MetaClass},再
   *    将 从(变量prop)的 {@link PropertyTokenizer#getChildren()}得到'name'传入 {@link MetaClass#hasSetter(String)}:
   *    将'name'包装成 {@link PropertyTokenizer} 并赋值给(变量prop，prop={name='name',index=null,children=null)，调用(变量prop)
   *    的 {@link PropertyTokenizer#hasNext()} 得到false,调用 {@link Reflector#hasSetter(String)}
   * </p>
   */
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  /**
   * 判断是否存在对应(参数name)的属性的getter方法,找出对应(参数name)的属性，最终调用
   * {@link Reflector#hasSetter(String)} 判断。
   * <p>
   *    将(参数name='order[0].name')传入该方法，将(参数name)包装成 {@link PropertyTokenizer} 并
   *    赋值给(变量prop，prop={name='order',index='0',children='name'),调用（变量prop)的
   *    {@link PropertyTokenizer#hasNext()}得到true,将 从(变量prop)的调用{@link PropertyTokenizer#getName()}
   *    得到'order'传入{@link Reflector#hasGetter(String)}得到true,再将 从(变量prop)的调用 {@link PropertyTokenizer#getName()}
   *    得到'order' 调用 {@link MetaClass#metaClassForProperty(String)}得到对应'order'的属性的 {@link MetaClass},再
   *    将 从(变量prop)的 {@link PropertyTokenizer#getChildren()}得到'name'传入 {@link MetaClass#hasGetter(String)}:
   *    将'name'包装成 {@link PropertyTokenizer} 并赋值给(变量prop，prop={name='name',index=null,children=null)，调用(变量prop)
   *    的 {@link PropertyTokenizer#hasNext()} 得到false,调用 {@link Reflector#hasGetter(String)} 判断
   * </p>
   */
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  /**
   * 直接调用{@link Reflector#getGetInvoker(String)}
   */
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  /**
   * 直接调用{@link Reflector#getSetInvoker(String)}
   */
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * 以name='Order[0].Name',builder=''作为参数调用：</br/>
   * 代码中：prop:{name='Order',index='0',children='Name'}，prop.hashNext=true,通过reflector查找出对应的
   * 属性名order赋值到propertyName变量中，若找到则propertyName!=null,builder='order.',
   * 再调用metaClassForPropety(propertyName)得到propertyName的MetaClass赋值给metaProp，再调用
   * metaProp.buildProperty(prop.getChildren(),builder)方法，
   * 此时prop.getChildren()='name',builder='order.name'
   * metaProp.buildProperty(prop.getChildren(),builder)方法会返回'order.name'.
   *
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  /**
   * 直接调用 {@link Reflector#hasDefaultConstructor()}
   * @return
   */
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
