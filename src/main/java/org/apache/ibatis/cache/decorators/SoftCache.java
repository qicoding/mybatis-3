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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * 软引用回收策略 缓存装饰类
 * <p>
 *    软引用只有当内存不足时才会被垃圾收集器回收。这里的实现机制中，使用了一个链表来保证一定数量的值即使内存不足也不会被回收，
 *    但是没有保存在该链表的值则有可能会被回收
 * </p>
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
public class SoftCache implements Cache {
  /** 硬列表，以避免GC,初始化对象实例为LinkedList  用于保存一定数量强引用的值 */
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  /**
   *  垃圾收集条目的队列
   *  <p>引用队列，当被垃圾收集器回收时，会将软引用对象放入此队列</p>
   *  <p>当GC回收时，会将对象</p>
   */
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  /** 真正的缓存类，委托Cache对象 */
  private final Cache delegate;
  /** 保存强引用值的数量,初始化为256 */
  private int numberOfHardLinks;

  public SoftCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  /**
   * 获取取{@link #delegate}的当前缓存数
   * @return
   */
  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    // 移除被垃圾收集器回收的键值
    removeGarbageCollectedItems();
    // 将软引用作用到Value中
    delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
  }

  /**
   * 获取缓存数据，获取的数据如果不为null，会将这个数据放入强引用队列中，队列会判断当前可容纳的数量，超过了就采用先进先出的策略进行移除。
   * @param key The key
   * @return 有可能返回null。因为GC可能已经清理相关数据
   */
  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
    SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
    if (softReference != null) {
      result = softReference.get();
      if (result == null) {
        // 该值被垃圾收集器回收，移除掉该项
        delegate.removeObject(key);
      } else {
        // 因为hardLinksToAvoidGarbageCollection非线程安全
        // 多线程情况下如果不加锁 hardLinksToAvoidGarbageCollection的size可能会超过设定的值numberOfHardLinks
        // See #586 (and #335) modifications need more than a read lock
        synchronized (hardLinksToAvoidGarbageCollection) {
          // 存入经常访问的键值到链表(最多256元素),防止垃圾回收
          hardLinksToAvoidGarbageCollection.addFirst(result);
          if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
            // 超出容量，则移除最先保存的引用
            // 因为加入是加入到列表的第一个位置，随意最后一个就是最先加入的元素。
            hardLinksToAvoidGarbageCollection.removeLast();
          }
        }
      }
    }
    return result;
  }

  /**
   * 删除对应{@link @key}的缓存数据
   * @param key The key
   * @return
   */
  @Override
  public Object removeObject(Object key) {
    // 移除被垃圾收集器回收的键值
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存数据
   * <p>
   *     清空强引用，移除被垃圾收集器回收的键值，清空缓存数据
   * </p>
   */
  @Override
  public void clear() {
    // 清空强引用
    synchronized (hardLinksToAvoidGarbageCollection) {
      hardLinksToAvoidGarbageCollection.clear();
    }
    // 移除被垃圾收集器回收的键值
    removeGarbageCollectedItems();
    // 清空缓存数据
    delegate.clear();
  }

  /**
   * 删除被垃圾收集器回收的键值
   */
  private void removeGarbageCollectedItems() {
    SoftEntry sv;
    // 清空被垃圾收集器回收的value其相关联的键以及软引用
    while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      delegate.removeObject(sv.key);
    }
  }

  /**
   * 继承了{@link SoftReference}，使得传进来的value转变成软引用。
   * <p>
   *     这里将其Value作为软引用，而不是用key，因为Key不能被回收，如果被移除的话，就会影响到整个体系，
   *     最底层的实现使用HashMap实现的，没有Key，就没有办法移除相关的值。反过来，值被回收了，将软引用对象放到队列中，
   *     可以根据Key调用removeObject移除该关联的键和软引用对象。
   * </p>
   */
  private static class SoftEntry extends SoftReference<Object> {
    /**
     *  保存与value相关联的Key，因为一旦被垃圾收集器回收，则此软引用对象会被放到关联的引用队列中，
     *  这样就可以根据Key，移除该键值对
     */
    private final Object key;

    SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue);
      this.key = key;
    }
  }

}
