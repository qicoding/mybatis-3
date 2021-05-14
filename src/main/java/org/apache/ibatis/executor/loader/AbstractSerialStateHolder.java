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
package org.apache.ibatis.executor.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.io.SerialFilterChecker;
import org.apache.ibatis.reflection.factory.ObjectFactory;

/**
 * 抽象串行状态保持器
 * 可以序列化，保存了原始对象、原始对象中未加载的属性Map、用来反射生成对象的相关信息
 * @author Eduardo Macarron
 * @author Franta Mejta
 */
public abstract class AbstractSerialStateHolder implements Externalizable {

  private static final long serialVersionUID = 8940388717901644661L;
  private static final ThreadLocal<ObjectOutputStream> stream = new ThreadLocal<>();
  /** 序列化后的对象 */
  private byte[] userBeanBytes = new byte[0];
  /** 原对象 */
  private Object userBean;
  /** 未加载的属性 */
  private Map<String, ResultLoaderMap.LoadPair> unloadedProperties;
  /** 对象工厂，创建对象时使用 */
  private ObjectFactory objectFactory;
  /** 构造函数的属性类型列表，创建对象时使用 */
  private Class<?>[] constructorArgTypes;
  /** 构造函数的属性列表，创建对象时使用 */
  private Object[] constructorArgs;

  public AbstractSerialStateHolder() {
  }

  public AbstractSerialStateHolder(
          final Object userBean,
          final Map<String, ResultLoaderMap.LoadPair> unloadedProperties,
          final ObjectFactory objectFactory,
          List<Class<?>> constructorArgTypes,
          List<Object> constructorArgs) {
    this.userBean = userBean;
    this.unloadedProperties = new HashMap<>(unloadedProperties);
    this.objectFactory = objectFactory;
    this.constructorArgTypes = constructorArgTypes.toArray(new Class<?>[0]);
    this.constructorArgs = constructorArgs.toArray(new Object[0]);
  }

  /**
   * 对对象进行序列化
   * @param out 序列化结果将存入的流
   * @throws IOException
   */
  @Override
  public final void writeExternal(final ObjectOutput out) throws IOException {
    boolean firstRound = false;
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream os = stream.get();
    if (os == null) {
      os = new ObjectOutputStream(baos);
      firstRound = true;
      stream.set(os);
    }

    os.writeObject(this.userBean);
    os.writeObject(this.unloadedProperties);
    os.writeObject(this.objectFactory);
    os.writeObject(this.constructorArgTypes);
    os.writeObject(this.constructorArgs);

    final byte[] bytes = baos.toByteArray();
    out.writeObject(bytes);

    if (firstRound) {
      stream.remove();
    }
  }

  /**
   * 对对象进行反序列化
   * @param in 反序列化输入的流
   * @throws IOException
   * @throws ClassNotFoundException
   */
  @Override
  public final void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
    final Object data = in.readObject();
    if (data.getClass().isArray()) {
      this.userBeanBytes = (byte[]) data;
    } else {
      this.userBean = data;
    }
  }

  /**
   * 反序列化时被调用，给出反序列化的对象
   * @return 最终给出的反序列化对象
   * @throws ObjectStreamException
   */
  @SuppressWarnings("unchecked")
  protected final Object readResolve() throws ObjectStreamException {
    // 非第一次运行，直接输出已经解析好的被代理对象
    /* Second run */
    if (this.userBean != null && this.userBeanBytes.length == 0) {
      return this.userBean;
    }

    SerialFilterChecker.check();

    // 第一次运行时，反序列化输出
    /* First run */
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(this.userBeanBytes))) {
      this.userBean = in.readObject();
      this.unloadedProperties = (Map<String, ResultLoaderMap.LoadPair>) in.readObject();
      this.objectFactory = (ObjectFactory) in.readObject();
      this.constructorArgTypes = (Class<?>[]) in.readObject();
      this.constructorArgs = (Object[]) in.readObject();
    } catch (final IOException ex) {
      throw (ObjectStreamException) new StreamCorruptedException().initCause(ex);
    } catch (final ClassNotFoundException ex) {
      throw (ObjectStreamException) new InvalidClassException(ex.getLocalizedMessage()).initCause(ex);
    }

    final Map<String, ResultLoaderMap.LoadPair> arrayProps = new HashMap<>(this.unloadedProperties);
    final List<Class<?>> arrayTypes = Arrays.asList(this.constructorArgTypes);
    final List<Object> arrayValues = Arrays.asList(this.constructorArgs);

    // 创建一个反序列化的代理输出，因此还是一个代理
    return this.createDeserializationProxy(userBean, arrayProps, objectFactory, arrayTypes, arrayValues);
  }

  protected abstract Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
          List<Class<?>> constructorArgTypes, List<Object> constructorArgs);
}
