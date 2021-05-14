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
package org.apache.ibatis.executor.loader.javassist;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;

import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.AbstractEnhancedDeserializationProxy;
import org.apache.ibatis.executor.loader.AbstractSerialStateHolder;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.loader.WriteReplaceInterface;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.Configuration;

/**
 * Javassist代理工厂
 * @author Eduardo Macarron
 */
public class JavassistProxyFactory implements org.apache.ibatis.executor.loader.ProxyFactory {

  private static final String FINALIZE_METHOD = "finalize";
  private static final String WRITE_REPLACE_METHOD = "writeReplace";

  public JavassistProxyFactory() {
    try {
      Resources.classForName("javassist.util.proxy.ProxyFactory");
    } catch (Throwable e) {
      throw new IllegalStateException("Cannot enable lazy loading because Javassist is not available. Add Javassist to your classpath.", e);
    }
  }

  /**
   * 创建一个代理
   * @param target 代理的目标对象
   * @param lazyLoader 要懒加载的属性Map
   * @param configuration 配置
   * @param objectFactory 对象工厂
   * @param constructorArgTypes 构造方法参数类型列表
   * @param constructorArgs 构造方法参数类型
   * @return
   */
  @Override
  public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedResultObjectProxyImpl.createProxy(target, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
  }

  /**
   * 创建一个反序列化的代理
   * @param target 代理的目标对象
   * @param unloadedProperties 未加载的属性
   * @param objectFactory 对象工厂
   * @param constructorArgTypes 构造方法参数类型列表
   * @param constructorArgs 构造方法参数类型
   * @return
   */
  public Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedDeserializationProxyImpl.createProxy(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
  }

  static Object crateProxy(Class<?> type, MethodHandler callback, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {

    ProxyFactory enhancer = new ProxyFactory();
    // 创建的代理对象 是 原对象的子类
    enhancer.setSuperclass(type);

    try {
      // 获取类中的writeReplace方法
      type.getDeclaredMethod(WRITE_REPLACE_METHOD);
      // ObjectOutputStream will call writeReplace of objects returned by writeReplace
      if (LogHolder.log.isDebugEnabled()) {
        LogHolder.log.debug(WRITE_REPLACE_METHOD + " method was found on bean " + type + ", make sure it returns this");
      }
    } catch (NoSuchMethodException e) {
      // 如果没找到writeReplace方法，则设置代理类继承WriteReplaceInterface接口，该接口中有writeReplace方法
      enhancer.setInterfaces(new Class[] { WriteReplaceInterface.class });
    } catch (SecurityException e) {
      // nothing to do here
    }

    Object enhanced;
    Class<?>[] typesArray = constructorArgTypes.toArray(new Class[constructorArgTypes.size()]);
    Object[] valuesArray = constructorArgs.toArray(new Object[constructorArgs.size()]);
    try {
      enhanced = enhancer.create(typesArray, valuesArray);
    } catch (Exception e) {
      throw new ExecutorException("Error creating lazy proxy.  Cause: " + e, e);
    }
    ((Proxy) enhanced).setHandler(callback);
    return enhanced;
  }

  private static class EnhancedResultObjectProxyImpl implements MethodHandler {
    /** 被代理类 */
    private final Class<?> type;
    /** 要懒加载的属性Map */
    private final ResultLoaderMap lazyLoader;
    /** 是否是激进懒加载 */
    private final boolean aggressive;
    /** 能够触发懒加载的方法名“equals”, “clone”, “hashCode”, “toString”。这四个方法名在Configuration中被初始化。 */
    private final Set<String> lazyLoadTriggerMethods;
    /** 对象工厂 */
    private final ObjectFactory objectFactory;
    /** 被代理类构造函数的参数类型列表 */
    private final List<Class<?>> constructorArgTypes;
    /** 被代理类构造函数的参数列表 */
    private final List<Object> constructorArgs;

    private EnhancedResultObjectProxyImpl(Class<?> type, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      this.type = type;
      this.lazyLoader = lazyLoader;
      this.aggressive = configuration.isAggressiveLazyLoading();
      this.lazyLoadTriggerMethods = configuration.getLazyLoadTriggerMethods();
      this.objectFactory = objectFactory;
      this.constructorArgTypes = constructorArgTypes;
      this.constructorArgs = constructorArgs;
    }

    public static Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      EnhancedResultObjectProxyImpl callback = new EnhancedResultObjectProxyImpl(type, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    /**
     * 代理类的拦截方法
     * @param enhanced 代理对象本身
     * @param method 被调用的方法
     * @param args 每调用的方法的参数
     * @param methodProxy 用来调用父类的代理
     * @return 方法返回值
     * @throws Throwable
     */
    @Override
    public Object invoke(Object enhanced, Method method, Method methodProxy, Object[] args) throws Throwable {
      // 取出被代理类中此次被调用的方法的名称
      final String methodName = method.getName();
      try {
        // 防止属性的并发加载
        synchronized (lazyLoader) {
          // 被调用的是writeReplace方法
          if (WRITE_REPLACE_METHOD.equals(methodName)) {
            Object original;
            if (constructorArgTypes.isEmpty()) {
              original = objectFactory.create(type);
            } else {
              original = objectFactory.create(type, constructorArgTypes, constructorArgs);
            }
            // 将被代理对象的属性拷贝进入新创建的对象
            PropertyCopier.copyBeanProperties(type, enhanced, original);
            if (lazyLoader.size() > 0) {
              // 则此时返回的信息要更多，不仅仅是原对象，还有相关的懒加载的设置等信息。因此使用CglibSerialStateHolder进行一次封装
              return new JavassistSerialStateHolder(original, lazyLoader.getProperties(), objectFactory, constructorArgTypes, constructorArgs);
            } else {
              // 没有未懒加载的属性了，那直接返回原对象进行序列化
              return original;
            }
          } else {
            // 存在懒加载属性且被调用的不是finalize方法
            if (lazyLoader.size() > 0 && !FINALIZE_METHOD.equals(methodName)) {
              // 如果是aggressive，则包括非懒加载属性的读操作也会触发懒加载
              // 同时几个触发方法也会引发所有属性的加载
              if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                lazyLoader.loadAll();
              } else if (PropertyNamer.isSetter(methodName)) {
                // 调用了赋值方法
                // 得到要赋值的属性名称
                final String property = PropertyNamer.methodToProperty(methodName);
                // 因为被赋值，因此该属性的懒加载操作直接取消，以赋值为准
                lazyLoader.remove(property);
              } else if (PropertyNamer.isGetter(methodName)) {
                // 获取读方法属性
                final String property = PropertyNamer.methodToProperty(methodName);
                // 判断对该属性是否有懒加载
                if (lazyLoader.hasLoader(property)) {
                  // 如果有，就加载下
                  lazyLoader.load(property);
                }
              }
            }
          }
        }
        return methodProxy.invoke(enhanced, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
  }

  private static class EnhancedDeserializationProxyImpl extends AbstractEnhancedDeserializationProxy implements MethodHandler {

    private EnhancedDeserializationProxyImpl(Class<?> type, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      super(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }

    public static Object createProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      EnhancedDeserializationProxyImpl callback = new EnhancedDeserializationProxyImpl(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    @Override
    public Object invoke(Object enhanced, Method method, Method methodProxy, Object[] args) throws Throwable {
      final Object o = super.invoke(enhanced, method, args);
      return o instanceof AbstractSerialStateHolder ? o : methodProxy.invoke(o, args);
    }

    @Override
    protected AbstractSerialStateHolder newSerialStateHolder(Object userBean, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      return new JavassistSerialStateHolder(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }
  }

  private static class LogHolder {
    private static final Log log = LogFactory.getLog(JavassistProxyFactory.class);
  }

}
