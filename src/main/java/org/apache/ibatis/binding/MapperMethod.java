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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * 映射方法
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  /** SQL命令 */
  private final SqlCommand command;
  /** 方法签名 */
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional()
              && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[]) array);
    }
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  /**
   * SQL命令
   * <p>
   *     通过mapperInterface+ method 找到的 MapperStatement对象,保存着MapperStatement对象的Id和SqlCommandType
   * </p>
   */
  public static class SqlCommand {

    /** 通过mapperInterface+ method 找到的 MapperStatement对象的Id */
    private final String name;
    /** 通过mapperInterface+ method 找到的 MapperStatement对象的SQL脚本类型 */
    private final SqlCommandType type;

    /**
     *
     * @param configuration Mybatis全局配置信息
     * @param mapperInterface 映射器接口类
     * @param method 方法对象
     */
    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 获取方法名
      final String methodName = method.getName();
      // 获取声明method的类
      final Class<?> declaringClass = method.getDeclaringClass();
      // 获取 mapperInterface+ methodName 对应的 MappedStatement对象
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      // 如果ms对象为null
      if (ms == null) {
        // 如果method加上了Flush注解
        if (method.getAnnotation(Flush.class) != null) {
          // 将 通过mapperInterface+ method 找到的 MapperStatement对象的Id 置为null
          name = null;
          // Sql命令类型设置成FLUSH
          type = SqlCommandType.FLUSH;
          // 如果没有加上Flush注解则抛出异常
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
        // 设置 通过mapperInterface+ method 找到的 MapperStatement对象的Id
        name = ms.getId();
        // 设置 ms的Sql命令类型
        type = ms.getSqlCommandType();
        // 如果ms的Sql命令类型为 UNKNOWN 则抛出异常
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * 获取 {@code mapperInterface} + {@code methodName} 对应的 MappedStatement对象
     * @param mapperInterface 映射器接口类
     * @param methodName 方法名
     * @param declaringClass 声明method的类
     * @param configuration Mybatis全局配置信息
     * @return {@code mapperInterface} + {@code methodName} 对应的 MappedStatement对象
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
      // 拼装statementId，取映射器接口包名+类名+'.'+方法名
      String statementId = mapperInterface.getName() + "." + methodName;
      // 如果存在statementId对应的MappedStatement对象
      if (configuration.hasStatement(statementId)) {
        // 取出对应statementId的MappedStatement对象,并返回
        return configuration.getMappedStatement(statementId);
        // 如果映射器接口类等于声明method的类
      } else if (mapperInterface.equals(declaringClass)) {
        // 直接返回null
        return null;
      }
      // 遍历映射器接口类继承的所有接口
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        // 如果声明method的类是superInterface的父类
        if (declaringClass.isAssignableFrom(superInterface)) {
          // 递归获取 superInterface + methodName 对应的 MappedStatement对象
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration);
          // 如果ms不为null
          if (ms != null) {
            return ms;
          }
        }
      }
      // 从继承的接口中去找都找不到时，就返回null
      return null;
    }
  }

  /**
   * 方法签名，封装着方法的返回类型的各个情况描述，指定类型的参数索引位置，以及参数名解析器
   */
  public static class MethodSignature {

    /** 如果返回类型为Collection的子类或者返回类型是数组，returnsMany为true */
    private final boolean returnsMany;
    /** 如果 {@link #mapKey} 不为null，returnMap为true */
    private final boolean returnsMap;
    /** 如果返回类型为Void,returnsVoid为true */
    private final boolean returnsVoid;
    /** 如果返回类型为Cursor类型，returnCursor为true */
    private final boolean returnsCursor;
    /** 如果返回类型为Optional类型，returnsOptional为true */
    private final boolean returnsOptional;
    /** 方法的返回类型 */
    private final Class<?> returnType;
    /** 只有当method的返回类型为Map，且有加上MapKey注解，就返回MapKey注解所定义的值；否则返回null */
    private final String mapKey;
    /** resultHandler类型参数在方法中索引位置 */
    private final Integer resultHandlerIndex;
    /** rowBounds类型参数在方法中索引位置 */
    private final Integer rowBoundsIndex;
    /** 参数名解析器： 参数名称解析器，用于构建sql脚本参数名+参数对象映射集合 */
    private final ParamNameResolver paramNameResolver;

    /**
     *
     * @param configuration Mybatis全局配置信息
     * @param mapperInterface 映射器接口类
     * @param method 方法对象
     */
    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 获取method在mapperInterface中的返回类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      // 如果返回类型为Class实例
      if (resolvedReturnType instanceof Class<?>) {
        // 将返回类型强转为Class类型，赋值给returnType
        this.returnType = (Class<?>) resolvedReturnType;
        // 参数化类型说明 参考： https://blog.csdn.net/JustBeauty/article/details/81116144
        // 如果返回类型为参数化类型，参数化类型
      } else if (resolvedReturnType instanceof ParameterizedType) {
        // 将返回类型强转为ParameterizedType类型，然后取出声明的类型，如List<T>，getRawType得到的就是List,然后强转声明类型为Class类型，赋值给returnType
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        // 取出方法的返回类型
        this.returnType = method.getReturnType();
      }
      // 如果返回类型为Void,returnsVoid为true
      this.returnsVoid = void.class.equals(this.returnType);
      // 如果返回类型为Collection的子类或者返回类型是数组，returnsMany为true
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      // 如果返回类型为Cursor类型，returnCursor为true
      this.returnsCursor = Cursor.class.equals(this.returnType);
      // 如果返回类型为Optional类型，returnsOptional为true
      this.returnsOptional = Optional.class.equals(this.returnType);
      // 有当method的返回类型为Map，且有加上MapKey注解，就返回MapKey注解所定义的值；否则返回null
      this.mapKey = getMapKey(method);
      // 如果mapKey不为null，returnMap为true
      this.returnsMap = this.mapKey != null;
      // 获取RowBounds.class 在 method 的参数类型数组中的唯一位置,赋值给rowBoundsIndex
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      // 获取ResultHandler.class 在 method 的参数类型数组中的唯一位置,赋值给rowBoundsIndex
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      // 新建一个参数名解析器： 参数名称解析器，用于构建sql脚本参数名+参数对象映射集合
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    /**
     * 将参数对象转换为sql脚本参数,返回sql脚本参数名-参数对象映射集合;
     * @param args 参数对象数值
     * @return  sql脚本参数名-参数对象映射集合
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    /**
     * 是否存在RowRounds类型参数
     * @return 存在返回true；否则返回false
     */
    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    /**
     * 提取RowBounds对象
     * @param args 参数对象数组
     * @return RowBounds对象
     */
    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    /**
     * 是否存在ResultHandler类型参数
     * @return 存在返回true；否则返回false
     */
    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    /**
     * 提取ResultHandler对象
     * @param args 参数对象数组
     * @return ResultHandler对象
     */
    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    /**
     * 获取 {@code paramType} 在 {@code method} 的参数类型数组中的唯一位置
     * @param method 方法对象
     * @param paramType 参数类型
     * @return {@code paramType} 在 {@code method} 的参数类型数组中的唯一位置，如果出现多个paramType的位置，会抛出异常
     */
    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      // 获取method的参数类型数组
      final Class<?>[] argTypes = method.getParameterTypes();
      // 遍历参数类型数组
      for (int i = 0; i < argTypes.length; i++) {
        // 如果paramType为argType[i]的父类或者相同
        if (paramType.isAssignableFrom(argTypes[i])) {
          // 如果位置为null 赋值index
          if (index == null) {
            index = i;
            // 如果出现多个paramType的位置，抛出异常
          } else {
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    public String getMapKey() {
      return mapKey;
    }

    /**
     * 获取MapKey
     * @param method 方法对象
     * @return 只有当method的返回类型为Map，且有加上MapKey注解，就返回MapKey注解所定义的值；否则返回null
     */
    private String getMapKey(Method method) {
      String mapKey = null;
      // 如果method的返回类型是Map的子类
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        // 获取method中的MapKey注解
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        // 如果method有加上MapKey注解
        if (mapKeyAnnotation != null) {
          // 获取MapKey注解的值
          mapKey = mapKeyAnnotation.value();
        }
      }
      // 只要当method的返回类型为Map，且有加上MapKey注解，mapKey才不为null
      return mapKey;
    }
  }

}
