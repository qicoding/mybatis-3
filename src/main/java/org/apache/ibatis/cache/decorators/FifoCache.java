/**
 *    Copyright 2009-2019 the original author or authors.
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

import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * 使用先进先出缓存策略的缓存装饰类
 * FIFO (first in, first out) cache decorator.
 *
 * @author Clinton Begin
 */
public class FifoCache implements Cache {

  /** 委托Cache对象 */
  private final Cache delegate;
  /**
   * 键列表
   * <p>
   *     Deque是Queue的子接口,我们知道Queue是一种队列形式,而Deque则是双向队列,它支持从两个端点方向检索和插入元素,
   *     因此Deque既可以支持FIFO形式也可以支持LIFO形式.Deque接口是一种比Stack和Vector更为丰富的抽象数据形式,因为它同时实现了以上两者.
   * </p>
   * <p>
   *     初始化时，该属性的实现类默认是{@link LinkedList}
   * </p>
   */
  private final Deque<Object> keyList;
  /** 最大缓存数，初始化时为1024 */
  private int size;

  public FifoCache(Cache delegate) {
    this.delegate = delegate;
    this.keyList = new LinkedList<>();
    this.size = 1024;
  }

  /**
   * 取{@link #delegate}的ID
   * @return
   */
  @Override
  public String getId() {
    return delegate.getId();
  }

  /**
   * 取{@link #delegate}的当前缓存数
   * @return
   */
  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 设置最大缓存数
   * @param size
   */
  public void setSize(int size) {
    this.size = size;
  }

  /**
   * 添加数据进缓存
   * @param key 可以是任何对象，但一般是CacheKey对象
   * @param value 查询结果
   */
  @Override
  public void putObject(Object key, Object value) {
    cycleKeyList(key);
    delegate.putObject(key, value);
  }

  /**
   * 从{@link #delegate}中获取{@link @key}对应的缓存数据
   * @param key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    return delegate.getObject(key);
  }

  /**
   * 从{@link #delegate}中删除{@link @key}对应的缓存数据
   * @param key
   * @return
   */
  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存，{@link #delegate}，{@link #keyList}都会清空
   */
  @Override
  public void clear() {
    delegate.clear();
    keyList.clear();
  }

  /**
   * 周期检查健名列表
   * <p>
   *     将{@link @key}记录到{@link #keyList}中，再判断{@link #keyList}大小是否超过设定的最大值，超过就取出{@link #keyList}的
   *     第一个元素赋值给{@link @oldestKey}，然后删掉{@link #delegate}的{@link @oldestKey}缓存
   * </p>
   * @param key
   */
  private void cycleKeyList(Object key) {
    keyList.addLast(key);
    if (keyList.size() > size) {
      Object oldestKey = keyList.removeFirst();
      delegate.removeObject(oldestKey);
    }
  }

}
