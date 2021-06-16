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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.util.MapUtil;

/**
 * 反射信息类。能在这里更加便捷的拿到其类的属性，getter,setter,setter参数类型，getter的返回类型等。
 * 值得注意的地方，该类整理的属性名并不是从类中直接getDeclaredField方法，或者是getField方法中获取
 * 而是从getter,setter方法名中解析拼装出来的，这一点使得POJO的扩展性提高很多，比如，我并没有在类中定义一个order的属性，
 * 但是我加上了其getOrder或setOrder方法，Reflector还是会认为有一个order属性在你定义的类中
 *
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  /** 对应的Class 类型，当前类 */
  private final Class<?> type;
  /** 可读属性的名称集合，可读属性就是存在相应getter 方法的属性，初始值为空数组 */
  private final String[] readablePropertyNames;
  /** 可写属性的名称集合，可写属性就是存在相应setter 方法的属性，初始值为空数组 */
  private final String[] writablePropertyNames;
  /** 属性相应的setter 方法， key 是属性名称， value 是Invoker 对象，它是对setter 方法对应 */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /** 属性相应的getter 方法， key 是属性名称， value 是Invoker 对象，它是对setter 方法对应 */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /** 属性相应的setter 方法的参数值类型， ke y 是属性名称， value 是setter 方法的参数类型 */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /** 属性相应的getter 方法的返回位类型， key 是属性名称， value 是getter 方法的返回位类型 */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /** 默认构造方法,无参构造方法 */
  private Constructor<?> defaultConstructor;

  /**
   * 所有属性名称的集合(不分大小写)，key=属性名去大写，value=类中真正的属性名,
   * 这里通过设置key全部大小，能够保证就算写得再烂的驼峰命名，可以拿到正确的属性。
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    // 查找clazz的无参构造方法，通过反射遍历所有构造方法，找到构造参数集合长度为0的。
    addDefaultConstructor(clazz);
    // 处理clazz 中的getter 方法，填充getMethods 集合和getTypes 集合
    addGetMethods(clazz);
    // 处理clazz 中的set ter 方法，填充setMethods 集合和set Types 集合
    addSetMethods(clazz);
    // 处理没有get/set的方法字段
    addFields(clazz);
    // 初始化可读写的名称集合
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 初始化caseInsensitivePropertyMap ，记录了所有大写格式的属性名称
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 将clazz的无参构造方法设置给defaultConstructor
   */
  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    // 过滤出无参构造
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  /**
   * 将clazz的get方法添加给getMethods
   */
  private void addGetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    Method[] methods = getClassMethods(clazz);
    // 过滤参数是0并且是getter的方法
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决 Getter 冲突
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 解决 Getter 冲突
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历 conflictingGetters
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 暂存获胜的 method
      Method winner = null;
      // 获取属性名
      String propName = entry.getKey();
      // 不明确标识 true 说明是不明确的
      boolean isAmbiguous = false;
      // 遍历Method
      for (Method candidate : entry.getValue()) {
        // winner 为空 则直接赋值第一个method
        if (winner == null) {
          winner = candidate;
          continue;
        }
        // 获胜Method的返回类型
        Class<?> winnerType = winner.getReturnType();
        // 当前Method的返回类型
        Class<?> candidateType = candidate.getReturnType();
        // 如果 获胜Method的返回类型 与 当前Method的返回类型 相同
        if (candidateType.equals(winnerType)) {
          // 当前Method的返回类型不是boolean类型 说明是不明确的 直接跳出循环
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
            // 当前Method的方法名是is开头的 则 当前Method获胜
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
          // 如果 当前Method的返回类型 是 获胜Method的返回类型 的父类 什么都不做
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
          // 如果 获胜Method的返回类型 是当 前Method的返回类型 的父类 则当前Method获胜
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
          // 如果都不是以上的情况 说明是不明确的 直接跳出循环
        } else {
          isAmbiguous = true;
          break;
        }
      }
      // 添加get方法
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  /**
   * 添加get方法
   * @param name
   * @param method
   * @param isAmbiguous
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 如果是不明确的 返回一个 不明确的方法调用者 否则 new一个方法调用者
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    getMethods.put(name, invoker);
    // 获取method在type中的返回类型
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    getTypes.put(name, typeToClass(returnType));
  }

  /**
   * 将clazz的set方法添加给setMethods
   */
  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Method[] methods = getClassMethods(clazz);
    // 过滤方法参数只有一个且是set开头的方法
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决setter方法冲突
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 添加方法method进conflictingMethods上。
   * @param conflictingMethods
   * @param name
   * @param method
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    // 判断是有效的属性名称
    if (isValidPropertyName(name)) {
      List<Method> list = MapUtil.computeIfAbsent(conflictingMethods, name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  /**
   * 解决setter方法冲突
   * @param conflictingSetters
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    // 遍历conflictingSetters
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      // 属性名
      String propName = entry.getKey();
      // 取出setter方法
      List<Method> setters = entry.getValue();
      // 从getter 方法的返回位类型中查找该属性的类型
      Class<?> getterType = getTypes.get(propName);
      // Getter方法的调用者是否不明确的
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      // 标识Setter方法不明确 false 说明是明确的 即 默认 setter方法是明确的
      boolean isSetterAmbiguous = false;
      Method match = null;
      // 遍历setters
      for (Method setter : setters) {
        // 如果Getter方法调用者是明确的 并且 setter方法的入参类型与getter方法的返回类型一致 则匹配成功setter方法 直接跳出循环
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        // 如果setter方法明确
        if (!isSetterAmbiguous) {
          // 选择最适合setter方法
          match = pickBetterSetter(match, setter, propName);
          // 如果match为null 则 说明setter不明确 设置为true
          isSetterAmbiguous = match == null;
        }
      }
      // 如果匹配的setter不为空则增加setter方法
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 选择最适合的setter方法
   * @param setter1 第一个setter方法
   * @param setter2 第二个setter方法
   * @param property 属性名
   * @return
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    // 第一个setter为空 则直接返回第二个setter
    if (setter1 == null) {
      return setter2;
    }
    // 分别后去setter1和setter2的第一个参数类型
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // 如果 setter1的参数类型 是 setter2参数类型的 父类 则返回setter2 即默认子类更适合
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
      // 如果 setter2的参数类型 是 setter1参数类型的 父类 则返回setter1 即默认用子类
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    // 创建一个不明确的setter方法调用者
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    // 获取setter1在type中的参数类型数组
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    // 设置第一个参数类型
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  /**
   * 增加setter方法
   */
  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    // 获取method在type中的参数类型数组
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    // 设置第一个参数类型
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  /**
   * 类型转换成类，没有找到，默认返回Object
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 普通Java类
    if (src instanceof Class) {
      result = (Class<?>) src;
      // 普通泛型类
    } else if (src instanceof ParameterizedType) {
      // 获取到原始类型
      result = (Class<?>) ((ParameterizedType) src).getRawType();
      // 泛型数组
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      // 普通Java类数组
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        // 泛型数组 通过递归获取Class
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  /**
   * 添加存在与类中，却没有getter方法或者没有setter方法的属性。包含父类的属性（递归）
   * @param clazz
   */
  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      // setter方法中不包含属性
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        // 获取修饰符标识
        int modifiers = field.getModifiers();
        // 如果修饰符不含 final、不含static 则追加setter方法
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // getter方法中不包含属性 直接追加 getter方法
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 父类不为空则继续递归
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  /**
   * 通过 Field 追加 setter 方法
   */
  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 通过 Field 追加 getter 方法
   */
  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 是有效的属性名称
   */
  private boolean isValidPropertyName(String name) {
    // 如果名称是 $ 开头 或者是 serialVersionUID 或者是 class 则说明不是有效属性名
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * 获取当前类以及父类中定义的所有方法的唯一签名以及相应的Method对象
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      // 为每个方法生成唯一签名，并记录到uniqueMethods集合中
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  /**
   * 返回的方法可能存在，两个相同的方法名称，因为当子类实现父类方法时且参数不同，
   * 此时生成的签名是不同的生成签名的规则是 方法返回值#方法名#参数名，那么就会返回两个相同的方法名
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    // 遍历 methods
    for (Method currentMethod : methods) {
      // 判断是不是桥接方法, 桥接方法是 JDK 1.5 引入泛型后，为了使Java的泛型方法生成的字节码和 1.5 版本前的字节码相兼容，由编译器自动生成的方法
      if (!currentMethod.isBridge()) {
        // 获取签名
        // 签名格式为：方法返回参数#方法名:参数名 ps：多个参数用,分割 签名样例:String#getName:User
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 如果签名存在，则不做处理，表示子类已经覆盖了该方法。
        // 如果签名不存在，则将签名作为Key,Method作为value 添加到uniqueMethods中
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 返回参数#方法名:参数名 ps：多个参数用,分割 签名样例:java.lang.String#getName:java.lang.String
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * 从java安全管理器中获取suppressAccessChecks反射权限是否存在，存在返回true,否则false
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  /**
   * 是否存在无参构造方法
   * @return
   */
  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  /**
   * 从setMethods计划中获取对应propertyName的setter方法的执行器，没有找到会抛出ReflectionException
   */
  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * 从getMethods计划中获取对应propertyName的getter方法的执行器，没有找到会抛出ReflectionException
   */
  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * 返回参数propertyName对应的setter方法的参数类型
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * 获取类中属性的getter方法的返回类型
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * 从getMethods中获取对应(参数propertyName)的setter方法
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * 从getMethods中获取对应(参数propertyName)的getter方法
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  /**
   * 将name改成全大小，然后从 不区分属性名称Map caseInsensitivePropertyMap 拿到类的真实属性名称
   */
  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
