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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 参数名解析器
 */
public class ParamNameResolver {

  /** 通用名称前缀 */
  public static final String GENERIC_NAME_PREFIX = "param";

  /** 使用实际参数名称 */
  private final boolean useActualParamName;

  /**
   * 存放参数的位置和名称
   *   key是索引，value是参数的名称。
   *   如果指定了名称，则从@Param中获得。如果未指定@Param，则使用参数索引。
   *   注意，当方法具有特殊参数(如RowBounds或ResultHandler)时，该索引可能与实际索引不同。
   *   Method(@Param("M") int a, @Param("N") int b)转化为map为{{0, "M"}, {1, "N"}}
   *   Method(int a, int b)转化为map为{{0, "0"}, {1, "1"}}
   *   Method(int a, RowBounds rb, int b)转化为map为{{0, "0"}, {2, "1"}}
   *   有序映射 存储参数名称
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  /**
   * 是否使用@Param注解标志
   */
  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    this.useActualParamName = config.isUseActualParamName();
    // 获取方法的参数类型数组
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取参数数组的注解数组 二维数组
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    // 遍历参数
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 如果是特殊参数则跳过
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      // 再遍历每个参数的注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        // 如果是 Param 注解
        if (annotation instanceof Param) {
          // 标识有@Param注解
          hasParamAnnotation = true;
          // 提取注解的value值作为参数名称
          name = ((Param) annotation).value();
          break;
        }
      }
      // 名称如果为空
      if (name == null) {
        // @Param was not specified.
        // 未指定@Param注解 使用实际参数名称
        if (useActualParamName) {
          name = getActualParamName(method, paramIndex);
        }
        // 名称还是为空时
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  /**
   * 获取实际参数名称
   * @param method
   * @param paramIndex
   * @return
   */
  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  /**
   * 是否特殊参数
   * @param clazz
   * @return
   */
  private static boolean isSpecialParameter(Class<?> clazz) {
    // 如果 RowBounds 或 ResultHandler 是 clazz的父类 或 一样
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * 返回 SQL 提供程序引用的参数名称
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * 获得命名参数
   * 返回一个没有名称的非特殊参数。
   * 使用命名规则命名多个参数。
   * 除了默认名称之外，此方法还添加了通用名称（param1、param2、...
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args
   *          the args
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    // 没有参数直接返回
    if (args == null || paramCount == 0) {
      return null;
      // 参数个数是1 并且 没有@Param注解
    } else if (!hasParamAnnotation && paramCount == 1) {
      Object value = args[names.firstKey()];
      return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
    } else {
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 添加通用参数名称 (param1, param2, ...)
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        // 确保不要覆盖以@Param 命名的参数
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * 如果对象是 {@link Collection} 或数组，则包装 {@link ParamMap} 返回
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object a parameter object
   * @param actualParamName an actual parameter name
   *                        (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    // 如果是集合
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      // 增加collection参数名
      map.put("collection", object);
      // 如果是List类型 则增加 list 参数名
      if (object instanceof List) {
        map.put("list", object);
      }
      // 实际名称不为空 也把实际参数名 追加
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
      // 如果是数组类型
    } else if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      // 增加array参数名
      map.put("array", object);
      // 实际名称不为空 也把实际参数名 追加
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    // 直接返回对象
    return object;
  }

}
