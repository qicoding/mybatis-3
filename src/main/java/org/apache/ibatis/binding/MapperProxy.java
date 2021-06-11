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
package org.apache.ibatis.binding;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.util.MapUtil;

/**
 * 映射器代理
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -4724728412955527868L;
  /** 允许的模式 */
  private static final int ALLOWED_MODES = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
      | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;
  /** Lookup的构造 只当privateLookupIn方法不存在是 该属性才会有值 */
  private static final Constructor<Lookup> lookupConstructor;
  /** privateLookupIn方法 该方法从jdk9之后才有的 */
  private static final Method privateLookupInMethod;
  /** 数据库会话对象 */
  private final SqlSession sqlSession;
  /** 映射器接口类 */
  private final Class<T> mapperInterface;
  /** 方法缓存Map,映射接口类方法对象-映射方法类对象 */
  private final Map<Method, MapperMethodInvoker> methodCache;

  /**
   * @param sqlSession 数据库会话对象
   * @param mapperInterface 映射器接口类
   * @param methodCache 方法缓存Map,映射接口类方法对象-映射方法类对象
   */
  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethodInvoker> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  static {
    Method privateLookupIn;
    try {
      // 获取 privateLookupIn方法 该方法从jdk9之后才有的
      privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    } catch (NoSuchMethodException e) {
      privateLookupIn = null;
    }
    // 赋值给对象属性
    privateLookupInMethod = privateLookupIn;

    Constructor<Lookup> lookup = null;
    // 如果 privateLookupIn 方法不存在
    if (privateLookupInMethod == null) {
      // JDK 1.8
      try {
        // 获取 Lookup 构造
        lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        // Lookup 构造设置为可访问
        lookup.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
            e);
      } catch (Exception e) {
        lookup = null;
      }
    }
    // 赋值 Lookup构造 给对象属性
    lookupConstructor = lookup;
  }

  /**
   * 代理方法回调
   * @param proxy 代理后的实例对象
   * @param method 对象被调用方法
   * @param args 调用时的参数
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // Method.getDeclaringClass:返回声明的该方法的类
      // 如果声明method的类是Object
      if (Object.class.equals(method.getDeclaringClass())) {
        // 直接执行
        return method.invoke(this, args);
      } else {
        // 先获取到方法调用者再执行invoke
        return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * 获取方法调用者
   * @param method
   * @return
   * @throws Throwable
   */
  private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
    try {
      return MapUtil.computeIfAbsent(methodCache, method, m -> {
        // Method.isDefault:如果此方法是默认方法，则返回true ; 返回false其他 默认方法是公共非抽象实例方法，即具有主体的非静态方法，以接口类型声明
        // 如果method是默认方法（默认方法是在接口中声明的公共非抽象实例方法）
        if (m.isDefault()) {
          try {
            // 不存在privateLookupIn方法 则使用 java8的方法句柄实现
            if (privateLookupInMethod == null) {
              return new DefaultMethodInvoker(getMethodHandleJava8(method));
              // 存在则用java9的方法句柄实现
            } else {
              return new DefaultMethodInvoker(getMethodHandleJava9(method));
            }
          } catch (IllegalAccessException | InstantiationException | InvocationTargetException
              | NoSuchMethodException e) {
            throw new RuntimeException(e);
          }
        } else {
          // 默认返回普通方法调用者
          return new PlainMethodInvoker(new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
        }
      });
    } catch (RuntimeException re) {
      Throwable cause = re.getCause();
      throw cause == null ? re : cause;
    }
  }

  /**
   * 获取方法句柄 - java9
   * @param method
   * @return
   * @throws NoSuchMethodException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  private MethodHandle getMethodHandleJava9(Method method)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    // 获取声明method的类
    final Class<?> declaringClass = method.getDeclaringClass();
    /*
     * privateLookupIn 方法 说明
     * 返回一个 {@link Lookup 查找对象}，具有模拟所有支持的字节码行为的完整功能，包括在目标类上的<a href="MethodHandles.Lookup.htmlprivacc">私有访问<a>。
     * 此方法检查是否允许指定为 {@code Lookup} 对象的调用者对目标类进行<em>深度反射<em>。
     * 如果 {@code m1} 是包含 {@link LookuplookupClass() 查找类} 的模块，而 {@code m2} 是包含目标类的模块，则此检查确保
     * <ul>
     *   <li>{@code m1 } {@link ModulecanRead 读取} {@code m2}.<li>
     *   <li>{@code m2} {@link ModuleisOpen(String,Module) opens}包含目标类的包至少为{@code m1}。 <li>
     *   <li>查找有{@link LookupMODULE MODULE}查找模式。<li>
     *  <ul>
     *  如果有安全管理器，则调用其{@code checkPermission}方法检查{@code ReflectPermission ("suppressAccessChecks")}。
     *  @apiNote {@code MODULE} 查找模式用于验证查找对象是由调用者模块中的代码创建的（或派生自最初由调用者创建的查找对象）。
     *  具有 {@code MODULE} 查找模式的查找对象可以与受信任方共享，而无需向调用方提供 {@code PRIVATE} 和 {@code PACKAGE} 访问权限。
     *  @param targetClass 目标类
     *  @param lookup 调用者查找对象
     */
    return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup())).findSpecial(
        declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
        declaringClass);
  }

  /**
   * 获取方法句柄 - java8
   * @param method
   * @return
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws InvocationTargetException
   */
  private MethodHandle getMethodHandleJava8(Method method)
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    // 获取声明method的类
    final Class<?> declaringClass = method.getDeclaringClass();
    /*
     * constructor.newInstance(declaringClass,
     *             MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
     *                 | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC)
     *                 :通过constructor构建MethodHandles.Lookup实例对象
     * MethodHandles.Lookup.unreflectSpecial:生成可以调用反射方法【declaringClass的method】的方法处理器
     * MethodHandle.bindTo(proxy)：绑定需要反射方法的对象
     * MethodHandle.invokeWithArguments:传入args，反射方法
     */
    return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass);
  }

  /**
   * 映射方法调用者接口
   */
  interface MapperMethodInvoker {
    /**
     * 执行方法
     * @param proxy 代理后的实例对象
     * @param method 对象被调用方法
     * @param args 调用方法的参数
     * @param sqlSession 数据库会话对象
     * @return
     * @throws Throwable
     */
    Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable;
  }

  /**
   * 普通方法调用者
   */
  private static class PlainMethodInvoker implements MapperMethodInvoker {
    private final MapperMethod mapperMethod;

    public PlainMethodInvoker(MapperMethod mapperMethod) {
      super();
      this.mapperMethod = mapperMethod;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return mapperMethod.execute(sqlSession, args);
    }
  }

  /**
   * 默认方法调用者
   */
  private static class DefaultMethodInvoker implements MapperMethodInvoker {
    private final MethodHandle methodHandle;

    public DefaultMethodInvoker(MethodHandle methodHandle) {
      super();
      this.methodHandle = methodHandle;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      /*
       * MethodHandle.bindTo(proxy)：绑定需要反射方法的对象
       * MethodHandle.invokeWithArguments:传入args，反射方法
       */
      return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
  }
}
