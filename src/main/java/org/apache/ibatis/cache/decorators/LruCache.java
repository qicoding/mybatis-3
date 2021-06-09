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
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * 最近最少使用的缓存策略的缓存装饰类
 * Lru (least recently used) cache decorator.
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  /** 真正存放缓存数据的Cache对象，委托类 */
  private final Cache delegate;
  /** 记录着每个key的访问次数 */
  private Map<Object, Object> keyMap;
  /** 最近最少用的元素的key */
  private Object eldestKey;

  /**
   * 设置委托的Cache对象，并初始化最大缓存数，默认是1024
   * @param delegate
   */
  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }

  /**
   * 获取{@link #delegate}的ID
   * @return
   */
  @Override
  public String getId() {
    return delegate.getId();
  }

  /**
   * 获取{@link #delegate}的缓存数
   * @return
   */
  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 设置最大缓存数
   * <p>
   *     每次调用该方法，都会重新新建一个LinedHashMap实例对赋值给{@link #keyMap},这意味着之前的keyMap所记录的每个元素的访问次数
   *     都会丢失，重新记录访问次数。而{@link #eldestKey}并不会置空，还是会在每次添加缓存数据后，删除对应{@link #eldestKey}的
   *     {@link #delegate}里的元素。之所以这样设计，推测可能是因为设计者任务重新记录每个元素的访问次数并不会造成太大的业务问题，并且
   *     既然重新调用了该方法，之前的扩容算法和初始化容量大小都应该都应该按照新的{@link @size}来重新设置，以保证性能的最佳
   * </p>
   * @param size
   */
  public void setSize(final int size) {
    /*
     * LinkedHashMap构造函数：
     * 第一个参数initialCapacity：初始化容量大小
     * 第2个参数loadFactor：后面如果LinkedHashMap需要增大长度，按照capacity*loadFactor取整后增长
     * 第3个参数accessOrder:
     * accessOrder设置为false，表示不是访问顺序而是插入顺序存储的，这也是默认值，表示LinkedHashMap中存储的顺序是按照调用put方法插入的顺序进行排序的
     */
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      /*
       * <p>
       * 该方法是LinkedHashMap提供的一个钩子方法，是交给用户自己根据业务实现的。该方法会在添加
       *  完元素后被调用，如果返回的是false，就不会删除最近最少使用的元素。默认是返回false。
       * </p>
       * <p>
       *   如果大于{@link LruCache#keyMap}的大小大于设定的{@link #size}，
       *    就会返回true,并将最近最少使用的元素的key赋值给{@link LruCache#eldestKey}
       * </p>
       * @param eldest
       * @return
       */
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
          // 记录最近最少使用的元素
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  /**
   * 添加缓存数据
   * <p>
   *     除了添加缓存数据到{@link #delegate}以外，还将{@link @key}加入到{@link #keyMap}中进行记录，
   *      并将将{@link #delegate}里最近最少用的元素删除。
   * </p>
   * @param key Can be any object but usually it is a {@link @CacheKey} 可以是任何对象，但一般是CacheKey对象
   * @param value The result of a select. 查询结果
   */
  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  /**
   * 获取缓存数据
   * <p>
   *     除了从{@link #delegate}中取出对应{@link @key}的缓存数据，{@link @keyMap}也会对{@link @key}记录访问次数。
   * </p>
   */
  @Override
  public Object getObject(Object key) {
    keyMap.get(key); // touch 这里会使得key在keyMap里的访问次数加1
    return delegate.getObject(key);
  }

  /**
   * 删除{@link #delegate}的对应的{@link @key}的缓存数据
   */
  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  /**
   * 清空{@link #delegate}和{@link #keyMap}的所有数据
   */
  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  /**
   * 周期检查健名列表
   * <p>
   *     将{@link @key}加入到{@link #keyMap}中进行记录，并将{@link #delegate}里最近最少用的元素删除。
   * </p>
   * @param key
   */
  private void cycleKeyList(Object key) {
    keyMap.put(key, key);
    // 将{@link #delegate}里最近最少用的元素删除。
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
