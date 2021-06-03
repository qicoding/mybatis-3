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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession.StrictMap;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.util.MapUtil;

/**
 * 主键生成器 （自增的，也就是数据库自增后如果需要知道值，就用这个，这个是将自增结果回填到对象中）
 * 比如 MySQL、PostgreSQL；会将执行SQL后从Statement中获取主键放到参数对象对应的属性里
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  private static final String SECOND_GENERIC_PARAM_NAME = ParamNameResolver.GENERIC_NAME_PREFIX + "2";

  /**
   * A shared instance.
   *
   * @since 3.4.3
   */
  public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

  private static final String MSG_TOO_MANY_KEYS = "Too many keys are generated. There are only %d target objects. "
      + "You either specified a wrong 'keyProperty' or encountered a driver bug like #1523.";

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    processBatch(ms, stmt, parameter);
  }

  /**
   * 将 {@code stmt} 返回的主键赋值到 {@code parameter}
   * @param ms  Mapper.xml文件的select,delete,update,insert这些DML标签的封装类
   * @param stmt 执行SQL 语句并返回它所生成结果的对象。
   * @param parameter 参数对象
   */
  public void processBatch(MappedStatement ms, Statement stmt, Object parameter) {
    // 拿到主键的属性名
    final String[] keyProperties = ms.getKeyProperties();
    if (keyProperties == null || keyProperties.length == 0) {
      // 没有主键则无需操作
      return;
    }
    // getGeneratedKeys:该方法获取由于执行此Statement对象而创建的所有自动生成的键
    // 调用Statement对象的getGeneratedKeys方法获取自动生成的主键值
    try (ResultSet rs = stmt.getGeneratedKeys()) {
      // 获取结果集的元信息
      final ResultSetMetaData rsmd = rs.getMetaData();
      final Configuration configuration = ms.getConfiguration();
      if (rsmd.getColumnCount() < keyProperties.length) {
        // Error?
        // 主键数目比结果的总字段数目还多，则发生了错误。
        // 但因为此处是获取主键这样的附属操作，因此忽略错误，不影响主流程
      } else {
        // 调用子方法，将主键值赋给实参
        assignKeys(configuration, rs, rsmd, keyProperties, parameter);
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    }
  }

  /**
   * 根据 {@code parameter} 的类型构建AssignKey对象，然后将 {@code rs} 的结果赋值到 {@code parameter} 的元素中
   * @param configuration mybatis全局配置新
   * @param rs 结果集
   * @param rsmd 结果集元信息
   * @param keyProperties 属性名数组
   * @param parameter 参数对象
   */
  @SuppressWarnings("unchecked")
  private void assignKeys(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd, String[] keyProperties,
      Object parameter) throws SQLException {
    // StrictMap,ParamMap:其功能甚至连代码都是一致的，都是HashMap的子类，都是对获取集合没有元素时会抛出异常。
    // 如果参数对象是StrictMap,ParamMap的实例时
    if (parameter instanceof ParamMap || parameter instanceof StrictMap) {
      // Multi-param or single param with @Param
      // 根据 parameter 构建KeyAssigner对象，然后将rs的对应数据赋值到 parameter 的对应的元素中
      assignKeysToParamMap(configuration, rs, rsmd, keyProperties, (Map<String, ?>) parameter);
      // 如果参数对象是ArrayList实例，且parameter是有元素的 且 获取parameter的第一个元素属于ParamMap实例
    } else if (parameter instanceof ArrayList && !((ArrayList<?>) parameter).isEmpty()
        && ((ArrayList<?>) parameter).get(0) instanceof ParamMap) {
      // Multi-param or single param with @Param in batch operation
      // 构建AssignKey对象，将 rs 的结果赋值到 paramMapList 的元素中
      assignKeysToParamMapList(configuration, rs, rsmd, keyProperties, (ArrayList<ParamMap<?>>) parameter);
    } else {
      // Single param without @Param
      // 构建AssignKey对象，将 rs 的结果赋值到 parameter 的元素中，如果parameter不是集合，会自动将parameter转换成集合
      assignKeysToParam(configuration, rs, rsmd, keyProperties, parameter);
    }
  }

  /**
   * 构建AssignKey对象，将 {@code rs} 的结果赋值到 {@code parameter} 的元素中，如果parameter不是集合，会自动将parameter转换成集合
   * @param configuration mybatis全局配置信息
   * @param rs 结果集
   * @param rsmd 结果集元素
   * @param keyProperties 属性名数组
   * @param parameter 参数对象
   * @throws SQLException
   */
  private void assignKeysToParam(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, Object parameter) throws SQLException {
    // 将parameter转换成Collection对象
    Collection<?> params = collectionize(parameter);
    // 如果params是空集合直接返回
    if (params.isEmpty()) {
      return;
    }
    // 定义一个分配者集合
    List<KeyAssigner> assignerList = new ArrayList<>();
    // 遍历属性数组
    for (int i = 0; i < keyProperties.length; i++) {
      // 构建分配者对象后添加到分配者集合
      assignerList.add(new KeyAssigner(configuration, rsmd, i + 1, null, keyProperties[i]));
    }
    // 获取参数对象的迭代器
    Iterator<?> iterator = params.iterator();
    // 遍历结果集
    while (rs.next()) {
      // 如果iterator没有下一个元素，即结果集还有结果，但是参数对象集合已经没有元素可以接收结果了
      if (!iterator.hasNext()) {
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, params.size()));
      }
      // 获取下一个参数对象
      Object param = iterator.next();
      // 变量分配者进行分配，将rs的对应columnPosition位置的列数据赋值到param对应的propertyName中
      assignerList.forEach(x -> x.assign(rs, param));
    }
  }

  /**
   * 构建AssignKey对象，将 {@code rs} 的结果赋值到 {@code paramMapList} 的元素中
   * @param configuration mybatis全局配置新
   * @param rs 结果集
   * @param rsmd 结果集元信息
   * @param keyProperties 配置的属性名数组
   * @param paramMapList ArrayList<ParamMap>类型的参数对象
   * @throws SQLException
   */
  private void assignKeysToParamMapList(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, ArrayList<ParamMap<?>> paramMapList) throws SQLException {
    // 获取paramMapList的迭代器
    Iterator<ParamMap<?>> iterator = paramMapList.iterator();
    // 初始化一个分配器集合
    List<KeyAssigner> assignerList = new ArrayList<>();
    // 计数器，表示已分配的结果数
    long counter = 0;
    // 遍历结果集
    while (rs.next()) {
      // 如果ParamMapList没有下一个元素，即结果集还有结果，但是参数对象集合已经没有元素可以接收结果了
      if (!iterator.hasNext()) {
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
      }
      // 获取参数对象的元素
      ParamMap<?> paramMap = iterator.next();
      // 如果分配器集合为空
      if (assignerList.isEmpty()) {
        // 遍历配置的属性数组
        for (int i = 0; i < keyProperties.length; i++) {
          // 从ParamMap中构建KeyAssigner并赋值到新的entry中，然后将entry的值添加到分配器集合里
          assignerList
              .add(getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i], keyProperties, false)
                  .getValue());
        }
      }
      // 遍历分配器集合，将rs的对应columnPosition位置的列数据赋值到paramMap对应的propertyName中
      assignerList.forEach(x -> x.assign(rs, paramMap));
      counter++;
    }
  }

  /**
   * 根据 {@code paramMap} 构建KeyAssigner对象，然后将 {@code rs} 的对应数据赋值到 {@code paramMap} 的对应的元素中
   * @param configuration Mybatis的全局配置信息
   * @param rs 结果集
   * @param rsmd 结果集元信息
   * @param keyProperties 配置的属性名数组
   * @param paramMap ParamMap类型或者StrictMap类型的参数对象
   */
  private void assignKeysToParamMap(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, Map<String, ?> paramMap) throws SQLException {
    // 如果参数对象没有元素直接返回
    if (paramMap.isEmpty()) {
      return;
    }
    // 新建一个分配映射集合
    Map<String, Entry<Iterator<?>, List<KeyAssigner>>> assignerMap = new HashMap<>();
    // 遍历属性名数组
    for (int i = 0; i < keyProperties.length; i++) {
      // 从ParamMap中构建KeyAssigner并赋值到新的entry中
      Entry<String, KeyAssigner> entry = getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i],
          keyProperties, true);
      // 分配映射集合中存在entry key则直接返回 不存在则创建返回
      Entry<Iterator<?>, List<KeyAssigner>> iteratorPair = MapUtil.computeIfAbsent(assignerMap, entry.getKey(),
          k -> MapUtil.entry(collectionize(paramMap.get(k)).iterator(), new ArrayList<>()));
      // 将entry存储的keyAssigner存放到iteratorPair的集合里
      iteratorPair.getValue().add(entry.getValue());
    }
    // 计数器，表示已分配的结果数
    long counter = 0;
    // 遍历结果集元素
    while (rs.next()) {
      // 遍历assignerMap的值集合
      for (Entry<Iterator<?>, List<KeyAssigner>> pair : assignerMap.values()) {
        // 如果参数对象的键对象已经没有下一个了,这里的应该只是取iterator的第一个元素而已，而这里判断他是否存在第一个元素，不存在就抛出异常
        if (!pair.getKey().hasNext()) {
          throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
        }
        // 获取下一个参数对象的健对象
        Object param = pair.getKey().next();
        // 分配，将rs的对应columnPosition位置的列数据赋值到param对应的propertyName中
        pair.getValue().forEach(x -> x.assign(rs, param));
      }
      counter++;
    }
  }

  /**
   * 从ParamMap中构建KeyAssigner并赋值到新的entry中
   * @param config Mybatis全局配置信息
   * @param rsmd 结果集元信息
   * @param columnPosition 列名位置
   * @param paramMap ParamMap类型或者StrictMap类型的参数对象
   * @param keyProperty 配置的属性名
   * @param keyProperties 配置的属性名数组
   * @param omitParamName 是否忽略参数名称
   */
  private Entry<String, KeyAssigner> getAssignerForParamMap(Configuration config, ResultSetMetaData rsmd,
      int columnPosition, Map<String, ?> paramMap, String keyProperty, String[] keyProperties, boolean omitParamName) {
    Set<String> keySet = paramMap.keySet();
    // A caveat : if the only parameter has {@code @Param("param2")} on it,
    // it must be referenced with param name e.g. 'param2.x'.
    // 只有一个参数
    boolean singleParam = !keySet.contains(SECOND_GENERIC_PARAM_NAME);
    int firstDot = keyProperty.indexOf('.');
    // 如果没有点
    if (firstDot == -1) {
      // 如果只有一个参数
      if (singleParam) {
        // 从单一的{@code paramMap}中构建KeyAssigner并保存到新的Entry中
        return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
      }
      // 如果不是只有一个参数值则抛出执行异常
      throw new ExecutorException("Could not determine which parameter to assign generated keys to. "
          + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
          + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
          + keySet);
    }
    // 截取前面的字符串为参数名
    String paramName = keyProperty.substring(0, firstDot);
    // 如果参数对象中有这个参数名
    if (keySet.contains(paramName)) {
      // 如果忽略参数名，argParamName为null;否则argParamName为singleParamName
      String argParamName = omitParamName ? null : paramName;
      // 截取属性名点后面的字符串为参数键属性名
      String argKeyProperty = keyProperty.substring(firstDot + 1);
      // 构建KeyAssigner并赋值到新的entry中
      return MapUtil.entry(paramName, new KeyAssigner(config, rsmd, columnPosition, argParamName, argKeyProperty));
      // 如果只有一个参数值
    } else if (singleParam) {
      // 从单一的{@code paramMap}中构建KeyAssigner并保存到新的Entry中
      return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
    } else {
      // 如果参数对象中没有这个参数名且不只有一个参数值时抛异常
      throw new ExecutorException("Could not find parameter '" + paramName + "'. "
          + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
          + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
          + keySet);
    }
  }

  /**
   * 从单一的{@code paramMap}中构建KeyAssigner并保存到新的Entry中
   * @param config Mybatis全局配置信息
   * @param rsmd 结果集元信息
   * @param columnPosition 列名位置
   * @param paramMap ParamMap类型或者StrictMap类型的参数对象
   * @param keyProperty 配置的属性名
   * @param omitParamName 是否忽略参数名称
   */
  private Entry<String, KeyAssigner> getAssignerForSingleParam(Configuration config, ResultSetMetaData rsmd,
      int columnPosition, Map<String, ?> paramMap, String keyProperty, boolean omitParamName) {
    // Assume 'keyProperty' to be a property of the single param.
    // 获取paramMap唯一的键名
    String singleParamName = nameOfSingleParam(paramMap);
    // 如果忽略参数名，argParamName为null;否则argParamName为singleParamName
    String argParamName = omitParamName ? null : singleParamName;
    // 构建KeyAssigner并赋值到新的entry中
    return MapUtil.entry(singleParamName, new KeyAssigner(config, rsmd, columnPosition, argParamName, keyProperty));
  }

  /**
   * 获取参数映射唯一健名
   * @param paramMap 参数映射
   * @return 参数映射唯一健名
   */
  private static String nameOfSingleParam(Map<String, ?> paramMap) {
    // There is virtually one parameter, so any key works.
    // 获取paramMap的键名Set，通过迭代器，获取第一个键名并返回
    return paramMap.keySet().iterator().next();
  }

  /**
   * 将 {@code param} 转换成 Collection对象
   * @param param 参数对象
   * @return 如果 {@code param} 不是Collection对象，都会转换成ArrayList对象；否则直接返回 {@code param} ;
   */
  private static Collection<?> collectionize(Object param) {
    // 如果param本来就是Collection的实现类
    if (param instanceof Collection) {
      // 不需要转换，直接返回
      return (Collection<?>) param;
      // 如果param是Object数组的实现了
    } else if (param instanceof Object[]) {
      // 将param转换成ArrayList对象
      return Arrays.asList((Object[]) param);
    } else {
      // 如果是普通java类，构建成ArrayList对象
      return Arrays.asList(param);
    }
  }

  /**
   * 键名分配者
   */
  private class KeyAssigner {
    /** mybatis全局配置信息 */
    private final Configuration configuration;
    /** 结果集元信息 */
    private final ResultSetMetaData rsmd;
    /** TypeHandler注册器 */
    private final TypeHandlerRegistry typeHandlerRegistry;
    /** 列名位置 */
    private final int columnPosition;
    /** 参数名 */
    private final String paramName;
    /** 属性名 */
    private final String propertyName;
    /** 类型处理器 */
    private TypeHandler<?> typeHandler;

    protected KeyAssigner(Configuration configuration, ResultSetMetaData rsmd, int columnPosition, String paramName,
        String propertyName) {
      super();
      this.configuration = configuration;
      this.rsmd = rsmd;
      this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      this.columnPosition = columnPosition;
      this.paramName = paramName;
      this.propertyName = propertyName;
    }

    /**
     * 分配，将rs的对应columnPosition位置的列数据赋值到param对应的propertyName中
     * @param rs 结果集
     * @param param 参数对象
     */
    protected void assign(ResultSet rs, Object param) {
      // 如果参数名不为null
      if (paramName != null) {
        // If paramName is set, param is ParamMap 如果设置了参数名称,参数是参数映射;
        // 从param获取paramName的值重新赋值给param
        param = ((ParamMap<?>) param).get(paramName);
      }
      // 构建param的元对象
      MetaObject metaParam = configuration.newMetaObject(param);
      try {
        // 如果typeHandler不为null
        if (typeHandler == null) {
          // 如果param元对象存在propertyName的setter方法
          if (metaParam.hasSetter(propertyName)) {
            // 获取propertyName的setter方法的属性类型
            Class<?> propertyType = metaParam.getSetterType(propertyName);
            // 根据属性类型，当前列名位置的jdbc类型取得对应的TypeHandler对象并赋值给typeHandler
            typeHandler = typeHandlerRegistry.getTypeHandler(propertyType,
              JdbcType.forCode(rsmd.getColumnType(columnPosition)));
          } else {
            // 如果param元对象不存在propertyName的setter方法抛异常
            throw new ExecutorException("No setter found for the keyProperty '" + propertyName + "' in '"
              + metaParam.getOriginalObject().getClass().getName() + "'.");
          }
        }
        // 如果typeHandler还是为null
        if (typeHandler == null) {
          // Error? 忽略
        } else {
          // 获取结果对象
          Object value = typeHandler.getResult(rs, columnPosition);
          // 将结果对赋值到param对应的propertyName中
          metaParam.setValue(propertyName, value);
        }
      } catch (SQLException e) {
        throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e,
          e);
      }
    }
  }
}
