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
package org.apache.ibatis.type;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.JapaneseDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;

/**
 * 类型处理注册器
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public final class TypeHandlerRegistry {

  /**
   * 枚举类JdbcType作为键，完成数据库类型与类型处理器的对应注册
   */
  private final Map<JdbcType, TypeHandler<?>>  jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);

  /**
   * Java类型作为键，JdbcType与TypeHandler的映射关系作为value，完成Java类型、数据库类型和类型处理器三者的对应注册
   */
  private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();

  /**
   * 对未知类型的注册，如Object
   * 其实还是在{@link BaseTypeHandler}的抽象方法中根据返回的结果集提供的列去获取对应的TypeHandler时候，
   * 在获取不到的情况下，就会使用{@link ObjectTypeHandler}处理
   */
  private final TypeHandler<Object> unknownTypeHandler;

  /**
   * 保存着所有的类型处理器实例，是以类型处理器的类类型为键值保存的，它可以统筹所有的类型处理器实例
   */
  private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();

  /**
   * 空TypeHandler集合的标识
   */
  private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();

  /**
   * 默认的枚举类型处理器类
   */
  private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

  /**
   * The default constructor.
   */
  public TypeHandlerRegistry() {
    this(new Configuration());
  }

  /**
   * 注册一些基本的，常用的TypeHandler
   * The constructor that pass the MyBatis configuration.
   *
   * @param configuration a MyBatis configuration
   * @since 3.5.4
   */
  public TypeHandlerRegistry(Configuration configuration) {
    this.unknownTypeHandler = new UnknownTypeHandler(configuration);

    register(Boolean.class, new BooleanTypeHandler());
    register(boolean.class, new BooleanTypeHandler());
    register(JdbcType.BOOLEAN, new BooleanTypeHandler());
    register(JdbcType.BIT, new BooleanTypeHandler());

    register(Byte.class, new ByteTypeHandler());
    register(byte.class, new ByteTypeHandler());
    register(JdbcType.TINYINT, new ByteTypeHandler());

    register(Short.class, new ShortTypeHandler());
    register(short.class, new ShortTypeHandler());
    register(JdbcType.SMALLINT, new ShortTypeHandler());

    register(Integer.class, new IntegerTypeHandler());
    register(int.class, new IntegerTypeHandler());
    register(JdbcType.INTEGER, new IntegerTypeHandler());

    register(Long.class, new LongTypeHandler());
    register(long.class, new LongTypeHandler());

    register(Float.class, new FloatTypeHandler());
    register(float.class, new FloatTypeHandler());
    register(JdbcType.FLOAT, new FloatTypeHandler());

    register(Double.class, new DoubleTypeHandler());
    register(double.class, new DoubleTypeHandler());
    register(JdbcType.DOUBLE, new DoubleTypeHandler());

    register(Reader.class, new ClobReaderTypeHandler());
    register(String.class, new StringTypeHandler());
    register(String.class, JdbcType.CHAR, new StringTypeHandler());
    register(String.class, JdbcType.CLOB, new ClobTypeHandler());
    register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
    register(JdbcType.CHAR, new StringTypeHandler());
    register(JdbcType.VARCHAR, new StringTypeHandler());
    register(JdbcType.CLOB, new ClobTypeHandler());
    register(JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(JdbcType.NVARCHAR, new NStringTypeHandler());
    register(JdbcType.NCHAR, new NStringTypeHandler());
    register(JdbcType.NCLOB, new NClobTypeHandler());

    register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
    register(JdbcType.ARRAY, new ArrayTypeHandler());

    register(BigInteger.class, new BigIntegerTypeHandler());
    register(JdbcType.BIGINT, new LongTypeHandler());

    register(BigDecimal.class, new BigDecimalTypeHandler());
    register(JdbcType.REAL, new BigDecimalTypeHandler());
    register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
    register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

    register(InputStream.class, new BlobInputStreamTypeHandler());
    register(Byte[].class, new ByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
    register(byte[].class, new ByteArrayTypeHandler());
    register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
    register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.BLOB, new BlobTypeHandler());

    register(Object.class, unknownTypeHandler);
    register(Object.class, JdbcType.OTHER, unknownTypeHandler);
    register(JdbcType.OTHER, unknownTypeHandler);

    register(Date.class, new DateTypeHandler());
    register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
    register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
    register(JdbcType.TIMESTAMP, new DateTypeHandler());
    register(JdbcType.DATE, new DateOnlyTypeHandler());
    register(JdbcType.TIME, new TimeOnlyTypeHandler());

    register(java.sql.Date.class, new SqlDateTypeHandler());
    register(java.sql.Time.class, new SqlTimeTypeHandler());
    register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

    register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

    register(Instant.class, new InstantTypeHandler());
    register(LocalDateTime.class, new LocalDateTimeTypeHandler());
    register(LocalDate.class, new LocalDateTypeHandler());
    register(LocalTime.class, new LocalTimeTypeHandler());
    register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
    register(OffsetTime.class, new OffsetTimeTypeHandler());
    register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
    register(Month.class, new MonthTypeHandler());
    register(Year.class, new YearTypeHandler());
    register(YearMonth.class, new YearMonthTypeHandler());
    register(JapaneseDate.class, new JapaneseDateTypeHandler());

    // issue #273
    register(Character.class, new CharacterTypeHandler());
    register(char.class, new CharacterTypeHandler());
  }

  /**
   * 为枚举类{@link Enum}设置默认的{@link TypeHandler}.默认是{@link EnumTypeHandler}
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    this.defaultEnumTypeHandler = typeHandler;
  }

  /**
   * 是否存在对应 {@link @javaType} 的 {@link TypeHandler},直接调用{@link #hasTypeHandler(Class, JdbcType)}
   * @param javaType
   * @return
   */
  public boolean hasTypeHandler(Class<?> javaType) {
    return hasTypeHandler(javaType, null);
  }

  /**
   * 是否存在对应{@link @javaTypeReference}的{@link TypeHandler},直接调用{@link #hasTypeHandler(TypeReference, JdbcType)},
   * 第二个参数{@link @jdbcType}为null
   * @param javaTypeReference
   * @return
   */
  public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
    return hasTypeHandler(javaTypeReference, null);
  }

  /**
   * 是否存在对应 {@link @javaType} 和 {@link @jdbcType} 的 {@link TypeHandler}
   * <p>
   *     先判断{@link @javaType} 是否为null，若为null，直接返回false。
   *      再尝试调用{@link #getTypeHandler(Type, JdbcType)}获取TypeHandler不为null返回true
   * </p>
   */
  public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
    return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
  }

  /**
   * 是否存在对应 {@link @javaTypeReference} 和 {@link @jdbcType} 的 {@link TypeHandler}
   * <p>
   *     先判断{@link @javaTypeReference} 是否为null，若为null，直接返回false。
   *      再尝试调用{@link #getTypeHandler(TypeReference, JdbcType)}获取TypeHandler不为null返回true
   * </p>
   */
  public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
    return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
  }

  /**
   * 获取TypeHandler实例
   * <p>
   *     从{@link #allTypeHandlersMap} 中获取对应{@link @handlerType}的{@link @handlerType}实例
   * </p>
   */
  public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
    return allTypeHandlersMap.get(handlerType);
  }

  /**
   * 获取TypeHandler实例
   * <p>
   *     将{@link @type}强转为{@link Type},然后调用{@link #getTypeHandler(Type, JdbcType)},第二个参数{@link @jdbcType}设置成null
   * </p>
   */
  public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    return getTypeHandler((Type) type, null);
  }

  /**
   * 获取TypeHandler实例
   * <p>
   *     直接调用{@link #getTypeHandler(TypeReference, JdbcType)},第二个参数{@link @jdbcType}设置成null
   * </p>
   */
  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
    return getTypeHandler(javaTypeReference, null);
  }

  /**
   * 获取TypeHandler实例
   * <p>
   *     从{@link #jdbcTypeHandlerMap}中获取对应{@link @jdbcType}
   * </p>
   */
  public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
    return jdbcTypeHandlerMap.get(jdbcType);
  }

  /**
   * 获取 TypeHandler
   * <p>
   *     将 {@link @type} 转换 {@link Type} 类型，然后调用 {@link TypeHandlerRegistry#getTypeHandler(Class, JdbcType)}
   * </p>
   */
  public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
    return getTypeHandler((Type) type, jdbcType);
  }

  /**
   * 获取 TypeHandler
   * <p>
   *     调用{@link @javaTypeReference}的{@link TypeReference#getRawType()}得到子类声明的对 T 泛型的声明的类型，
   *     然后传给{@link #getTypeHandler(Type, JdbcType)}得到对应的TypeHandler
   * </p>
   */
  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
    return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
  }

  /**
   * 根据 {@link @type} 和 {@link @jdbcType} 获取对应的 {@link TypeHandler}
   */
  @SuppressWarnings("unchecked")
  private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
    // {@link ParamMap)是一个HashMap<String,Object>的子类,它重写了get方法，使得如果尝试获取它没有的key，将会抛出{@link BindingException}
    if (ParamMap.class.equals(type)) {
      return null;
    }
    // 获取JdbcType、TypeHandler映射关系的map
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
    TypeHandler<?> handler = null;
    if (jdbcHandlerMap != null) {
      // 获取对应jdbcType的TypeHandler
      handler = jdbcHandlerMap.get(jdbcType);
      // 如果没有拿到，调用获取jdbcType为null值情况的TypeHandler
      if (handler == null) {
        handler = jdbcHandlerMap.get(null);
      }
      /*
       * 还是没有拿到，就由Mybatis自己选择一个TypeHandler：
       *     如果有多个不同类型的TypeHandler就无法选择TypeHandler,只能返回null;
       *     但是如果只有一个类型的TypeHandler,但有多个同类型的TypeHandler实例，就取最后一个实例
       */
      if (handler == null) {
        // #591
        handler = pickSoleHandler(jdbcHandlerMap);
      }
    }
    // type drives generics here
    // 就算handler=null，也不会抛出空指针或者转换异常
    return (TypeHandler<T>) handler;
  }

  /**
   * 获取JdbcType、TypeHandler映射关系的map
   * <p>
   *     先将{@link @type}传入从{@link #typeHandlerMap}中获取JdbcType、TypeHandler映射关系的map。如果找到就返回出去。
   *     如果没有获取到，判断是不是枚举类，还是普通java类。
   * </p>
   */
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
    // 空TypeHandler标识返回null也是符合场景的。
    if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) {
      return null;
    }
    // 没有找到该类的JdbcType、TypeHandler映射关系Map 而且 {@link @type} 是 Class类，
    if (jdbcHandlerMap == null && type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      // 属于枚举类型
      if (Enum.class.isAssignableFrom(clazz)) {
        // clazz若是枚举类型,clazz.getSuperclass()就得到java.lang.Enum，因为枚举类型不能extends,但是可以implement
        // 下面代码如果属于内部类，就会得到java.lang.Enum
        Class<?> enumClass = clazz.isAnonymousClass() ? clazz.getSuperclass() : clazz;
        // 从枚举类的接口获取JdbcType、TypeHandler映射关系的map
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(enumClass, enumClass);
        // 还是没有找到的话，就用defaultEnumTypeHandler的实例对象作为对应enumClass的TypeHandler
        if (jdbcHandlerMap == null) {
          register(enumClass, getInstance(enumClass, defaultEnumTypeHandler));
          return typeHandlerMap.get(enumClass);
        }
      } else {
        // 尝试通过获取父类去获取JdbcType、TypeHandler映射关系Map
        jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
      }
    }
    // 没有JdbcType、TypeHandler映射关系Map时，就用NULL_TYPE_HANDLER_MAP作为对应type的JdbcType、TypeHandler映射关系Map
    typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
    return jdbcHandlerMap;
  }

  /**
   * 从枚举类的接口获取JdbcType、TypeHandler映射关系的map
   * <p>
   *     通过递归该方法深入{@link @clazz}的所有接口，若找到{@link @clazz}接口对应的JdbcType、TypeHandler映射关系map【{@link @jdbcHandlerMap}】,
   *     就会根据{@link @jdbcHandlerMap}构建出{@link @enumClazz}对应的JdbcType、TypeHandler映射关系map。
   *     注意一下，只会找出第一个匹配的JdbcType、TypeHandler映射关系map赋值给{@link @jdbcHandlerMap}。若还是没有找到JdbcType、TypeHandler映射关系map，就会返回null
   * </p>
   */
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
    // 获取该枚举类所实现的所有接口
    for (Class<?> iface : clazz.getInterfaces()) {
      // 尝试通过接口类获取JdbcType、TypeHandler映射关系的map
      Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
      // 如果还是没有找到，通过递归的方式，深入这个接口中看看还有没有继承接口，从那些接口里尝试获取JdbcType、TypeHandler映射关系的map
      if (jdbcHandlerMap == null) {
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
      }
      // 找到了与接口对应的JdbcType、TypeHandler映射关系map后
      if (jdbcHandlerMap != null) {
        // Found a type handler regsiterd to a super interface
        // 构建一个新的JdbcType、TypeHandler映射关系map
        HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
        /*
         * 将jdbcHandlerMap的元素添加给newMap:
         * 将jdbcHandlerMap的key作为newMap的key,
         *  传入enumClazz以及通过jdbcHandlerMap的value获取value的Class来构建一个对应该枚举类的TypeHandler对象作为value
         */
        for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
          // Create a type handler instance with enum type as a constructor arg
          newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
        }
        // 找到了第一个就直接返回
        return newMap;
      }
    }
    return null;
  }

  /**
   * 通过获取父类去获取JdbcType、TypeHandler映射关系Map
   * <p>
   *     通过递归{@link @clazz}的父类，获取第一个对应父类的JdbcType、TypeHandler映射关系Map
   * </p>
   */
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
    Class<?> superclass =  clazz.getSuperclass();
    // 没有获取到父类 或者 递归父类去获取JdbcType、TypeHandler映射关系Map，都到了Object，返回null也是理所当然啦
    if (superclass == null || Object.class.equals(superclass)) {
      return null;
    }
    // 只会找出第一个匹配的JdbcType、TypeHandler映射关系map，
    // 就算真的有两个匹配的JdbcType、TypeHandler映射关系map，也不会合并且其JdbcType、TypeHandler映射关系map
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
    if (jdbcHandlerMap != null) {
      return jdbcHandlerMap;
    } else {
      // 递归父类
      return getJdbcHandlerMapForSuperclass(superclass);
    }
  }

  /**
   * 选择唯一的TypeHandler
   * <p>
   *     如果有多个不同类型的TypeHandler就无法选择TypeHandler,只能返回null;
   *     但是如果只有一个类型的TypeHandler,但有多个同类型的TypeHandler实例，就取最后一个实例
   * </p>
   * @param jdbcHandlerMap
   * @return
   */
  private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
    TypeHandler<?> soleHandler = null;
    for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
      if (soleHandler == null) {
        soleHandler = handler;
      } else if (!handler.getClass().equals(soleHandler.getClass())) {
        // More than one type handlers registered.
        // 不只一个TypeHandler类型的情况下
        return null;
      }
    }
    return soleHandler;
  }

  public TypeHandler<Object> getUnknownTypeHandler() {
    return unknownTypeHandler;
  }

  /**
   * 注册 jdbcType 对应的 TypeHandler
   * <p>
   *     直接调用以{@link @jdbcType}作为key 和 {@link @handler}作为value加入到{@link #jdbcTypeHandlerMap}中
   * </p>
   * @param jdbcType
   * @param handler
   */
  public void register(JdbcType jdbcType, TypeHandler<?> handler) {
    jdbcTypeHandlerMap.put(jdbcType, handler);
  }

  //
  // REGISTER INSTANCE
  //

  // Only handler

  /**
   * 注册TypeHandler
   * <ol>
   *     <li>先尝试获取注解{@link MappedTypes}声明的类来调用{@link #register(Class, TypeHandler)}进行注册</li>
   *     <li>再尝试将{@link @typeHandler}转换成{@link TypeReference},从而获取到声明的泛型类型
   *              来调用{@link #register(Class, TypeHandler)}进行注册</li>
   *     <li>都没有找到对应的java类型，就把javaType设置为null，调用{@link #register(Class, TypeHandler)}进行注册</li>
   * </ol>
   */
  @SuppressWarnings("unchecked")
  public <T> void register(TypeHandler<T> typeHandler) {
    // 一个标记，当注册了handledType就会变成true
    boolean mappedTypeFound = false;
    // 根据注解MappedType注册TypeHandler
    MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      for (Class<?> handledType : mappedTypes.value()) {
        register(handledType, typeHandler);
        mappedTypeFound = true;
      }
    }
    // 根据TypeReference声明的泛型类型注册TypeHandler
    // @since 3.1.0 - try to auto-discover the mapped type
    if (!mappedTypeFound && typeHandler instanceof TypeReference) {
      try {
        TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
        register(typeReference.getRawType(), typeHandler);
        mappedTypeFound = true;
      } catch (Throwable t) {
        // 可能用户定义的类型引用具有不同的类型，并且不可赋值，所以忽略它
        // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
      }
    }
    // 都没有找到对应的java类型，就当作null进行注册。
    if (!mappedTypeFound) {
      register((Class<T>) null, typeHandler);
    }
  }

  // java type + handler

  /**
   * 注册 javaType -- TypeHandle
   * 将{@link @javaType} 强转为 {@link Type}后，调用 {@link TypeHandlerRegistry#register(Type, TypeHandler)}
   */
  public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
    register((Type) javaType, typeHandler);
  }

  /**
   * 注册 javaType -- TypeHandle
   * <p>
   *    获取{@link @typeHandler}的{@link MappedJdbcTypes}注解信息赋值给{@link @mappedJdbcTypes}，
   *    在{@link @mappedJdbcTypes}不为null的情况下，遍历@mappedJdbcType所支持的jdbcType，
   *    对其调用 {@link TypeHandlerRegistry#register(Class, JdbcType, Class)} 注册，再判断是否支持null的jdbcType，
   *    支持的将null作为{@link TypeHandlerRegistry#register(Class, JdbcType, Class)}的第二参数进行调用；
   *    否则,直接将null作为{@link TypeHandlerRegistry#register(Class, JdbcType, Class)}的第二参数进行调用
   * </p>
   */
  private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
    MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
    if (mappedJdbcTypes != null) {
      for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
        register(javaType, handledJdbcType, typeHandler);
      }
      if (mappedJdbcTypes.includeNullJdbcType()) {
        register(javaType, null, typeHandler);
      }
    } else {
      register(javaType, null, typeHandler);
    }
  }

  /**
   * 注册 TypeHandler
   * <p>
   *     获取{@link @javaTypeReference}所声明的泛型类型，调用{@link #register(Class, TypeHandler)}进行注册
   * </p>
   */
  public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
    register(javaTypeReference.getRawType(), handler);
  }

  // java type + jdbc type + handler

  /**
   * 注册TypeHandler
   * <p>
   *     将{@link @type}强转成{@link Type},调用{@link #register(Type, JdbcType, TypeHandler)}进行注册
   * </p>
   */
  // Cast is required here
  @SuppressWarnings("cast")
  public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
    register((Type) type, jdbcType, handler);
  }

  /**
   * 注册 javaType -- jdbcType -- TypeHandler
   * <p>
   *     先从 {@link TypeHandlerRegistry#typeHandlerMap} 中获取{@link @javaType}对应的 JdbcType、TypeHandler映射关系的map
   *     并赋值给{@link @map},然后将{@link @jdbcType}作为key和{@link @handler}作为value设置到{@link @map}。
   *     简单来说就是合并原有的，新建没有的。
   *     最后还会获取{@link @handler}的Class类作为key，{@link @handler}作为value设置到{@link #allTypeHandlersMap}中
   * </p>
   * <p>
   *     只要{@link @map} 是 null,或者是一个{@link TypeHandlerRegistry#NULL_TYPE_HANDLER_MAP}都会对{@link @map}重新实例化
   *     一个新的{@link HashMap}并调用加入到 {@link #typeHandlerMap} 中。
   * </p>
   */
  private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
    if (javaType != null) {
      Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);
      if (map == null || map == NULL_TYPE_HANDLER_MAP) {
        map = new HashMap<>();
      }
      map.put(jdbcType, handler);
      typeHandlerMap.put(javaType, map);
    }
    allTypeHandlersMap.put(handler.getClass(), handler);
  }

  //
  // REGISTER CLASS
  //

  // Only handler type

  /**
   * 注册TypeHandler
   * <p>
   *     通过注解{@link MappedTypes}获取{@link @typeHandlerClass}能处理的java类型，逐一调用{@link #register(Class, Class)}进行注册，
   *     没有注册到指定类型+TypeHandler映射关系时，用null当作javaTypeClass参数，调用{@link #getInstance(Class, Class)}得到TypeHandler的实例，
   *     然后调用{@link #register(TypeHandler)}进行注册。
   * </p>
   */
  public void register(Class<?> typeHandlerClass) {
    boolean mappedTypeFound = false;
    MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      for (Class<?> javaTypeClass : mappedTypes.value()) {
        register(javaTypeClass, typeHandlerClass);
        mappedTypeFound = true;
      }
    }
    if (!mappedTypeFound) {
      register(getInstance(null, typeHandlerClass));
    }
  }

  // java type + handler type

  /**
   * 注册TypeHandler
   * <p>
   *     调用{@link Resources#classForName(String)}获取{@link @javaTypeClassName}和{@link @typeHandlerClassName}的Class,
   *     再调用{@link #register(Class, Class)}
   * </p>
   */
  public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
    register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
  }

  /**
   * 注册TypeHandler
   * <p>
   *     传入{@link @javaTypeClass}和{@link @typeHandlerClass}到{@link #getInstance(Class, Class)}中得到TypeHandler的实例对象，
   *     再调用{@link #register(TypeHandler)}进行注册
   * </p>
   */
  public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
  }

  // java type + jdbc type + handler type

  /**
   * 注册TypeHandler
   * <p>
   *     传入{@link @javaTypeClass}和{@link @typeHandlerClass}到{@link #getInstance(Class, Class)}中得到TypeHandler的实例对象,
   *     再调用{@link #register(Type, JdbcType, TypeHandler)}进行注册
   * </p>
   */
  public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
    register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
  }

  // Construct a handler (used also from Builders)

  /**
   * 创建TypeHandler实例对象
   * <p>
   *   当{@link @javaTypeClass}不为null，会尝试通过带 Class 参数的构造方法进行实例化TypeHandler对象。
   *   如果没有该构造方法，就通过无参构造方法实例化TypeHandler对象。
   * </p>
   */
  @SuppressWarnings("unchecked")
  public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    if (javaTypeClass != null) {
      try {
        /*
         * 尝试通过带 Class 参数的构造方法进行实例化TypeHandler对象。因为没有检查是否存在某个构造函数的方法，所以
         * 使用捕捉异常的方式解决.
         */
        Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
        return (TypeHandler<T>) c.newInstance(javaTypeClass);
      } catch (NoSuchMethodException ignored) {
        // ignored
      } catch (Exception e) {
        throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
      }
    }
    try {
      // 通过无参构造方法实例化TypeHandler
      Constructor<?> c = typeHandlerClass.getConstructor();
      return (TypeHandler<T>) c.newInstance();
    } catch (Exception e) {
      throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
    }
  }

  // scan

  /**
   * 扫描包中的 {@link TypeHandler} 的子类，并 调用 {@link TypeHandlerRegistry#register(TypeHandler)}
   * @param packageName
   */
  public void register(String packageName) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
    for (Class<?> type : handlerSet) {
      //Ignore inner classes and interfaces (including package-info.java) and abstract classes
      if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
        register(type);
      }
    }
  }

  // get information

  /**
   * 获取所有已注册的TypeHandler
   * Gets the type handlers.
   *
   * @return the type handlers
   * @since 3.2.2
   */
  public Collection<TypeHandler<?>> getTypeHandlers() {
    return Collections.unmodifiableCollection(allTypeHandlersMap.values());
  }

}
