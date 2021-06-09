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
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * 为缓存提供者的接口(SPI:串行外设接口。)
 * SPI for cache providers.
 * <p>
 *   每个命名空间都会创建一个缓存的实例对象
 * One instance of cache will be created for each namespace.
 * <p>
 * The cache implementation must have a constructor that receives the cache id as an String parameter.
 * 缓存实现必须有一个构造函数，它接收缓存 ID 作为字符串参数
 * <p>
 * MyBatis will pass the namespace as id to the constructor.
 * MyBatis 会将命名空间作为 id 传递给构造函数
 *
 * <pre>
 * public MyCache(final String id) {
 *   if (id == null) {
 *     throw new IllegalArgumentException("Cache instances require an ID");
 *   }
 *   this.id = id;
 *   initialize();
 * }
 * </pre>
 *
 * @author Clinton Begin
 */

public interface Cache {

  /**
   * 获取缓存对象的唯一标识,一般是Mapper.xml的命名空间
   * @return The identifier of this cache
   */
  String getId();

  /**
   * 保存key/value到缓存对象中
   * @param key 可以是任何对象，但一般是CacheKey对象
   *          Can be any object but usually it is a {@link CacheKey}
   * @param value 查询结果
   *          The result of a select.
   */
  void putObject(Object key, Object value);

  /**
   * 从缓存对象中获取key对应的value
   * @param key
   *          The key
   * @return The object stored in the cache.
   */
  Object getObject(Object key);

  /**
   * 移除key对应的value
   *
   * 从 3.3.0 开始，仅在回滚缓存中丢失的任何先前值期间调用此方法。
   * 这允许任何阻塞缓存释放先前可能已放在密钥上的锁。
   * 阻塞缓存在值为空时放置一个锁，并在该值再次返回时释放它。
   * 这样其他线程将等待该值可用而不是访问数据库。
   *
   * As of 3.3.0 this method is only called during a rollback
   * for any previous value that was missing in the cache.
   * This lets any blocking cache to release the lock that
   * may have previously put on the key.
   * A blocking cache puts a lock when a value is null
   * and releases it when the value is back again.
   * This way other threads will wait for the value to be
   * available instead of hitting the database.
   *
   *
   * @param key
   *          The key
   * @return Not used
   */
  Object removeObject(Object key);

  /**
   * 清空缓存
   * Clears this cache instance.
   */
  void clear();

  /**
   * 可选的。这个方法不被核心调用。
   * Optional. This method is not called by the core.
   * 获取缓存对象中存储的键/值对的数量
   * @return The number of elements stored in the cache (not its capacity).
   */
  int getSize();

  /**
   * 获取读写锁，这个方法mybatis的所有Cache实现类都没有重写过，都是直接返回null
   * Optional. As of 3.2.6 this method is no longer called by the core.
   * 可选的。从 3.2.6 开始，框架核心不再调用此方法。
   * <p>
   * Any locking needed by the cache must be provided internally by the cache provider.
   *
   * @return A ReadWriteLock
   */
  default ReadWriteLock getReadWriteLock() {
    return null;
  }

}
