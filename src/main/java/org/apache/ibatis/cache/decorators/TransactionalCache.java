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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 事务缓存装饰类 2级缓存事务缓冲区
 * 此类保存在会话期间要添加到二级缓存的所有缓存条目。
 * 如果 Session 回滚，则在调用 commit 或丢弃时将条目发送到缓存。添加了阻塞缓存支持。
 * 因此，任何返回缓存未命中的 get() 都将跟随一个 put()，因此可以释放与该键关联的任何锁。
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  /** 真正存放缓存数据的Cache对象，委托类 */
  private final Cache delegate;
  /** 是否在commit时清除二级缓存的标记 */
  private boolean clearOnCommit;
  /** 需要在commit时提交到二级缓存的数据 */
  private final Map<Object, Object> entriesToAddOnCommit;
  /** 缓存未命中的数据，事务commit时，也会放入二级缓存（key,null） */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 获取缓存数据
   * <p>
   *     首先会在对应的二级缓存对象中查询，并将未命中的key记录到entriesMissedInCache中，之后根据clearOnCommit决定返回值
   * </p>
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key);
    if (object == null) {
      // 记录未命中的key
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  /**
   * 将没有提交的数据记录下来
   * @param key Can be any object but usually it is a {@link @CacheKey} 可以是任何对象，但一般是CacheKey对象
   * @param object
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 清空entriesToAddOnCommit，并且设置clearOnCommit为true
   */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    if (clearOnCommit) {
      // 清空二级缓存
      delegate.clear();
    }
    // 将数据刷新到二级缓存
    flushPendingEntries();
    // 重置clearOnCommit为false，清空entriesToAddOnCommit、entriesMissedInCache集合
    reset();
  }

  /**
   * 回退
   * <p>
   *     根据{@link #entriesMissedInCache}，删除二级缓存的数据，重置clearOnCommit为false，
   *      清空entriesToAddOnCommit、entriesMissedInCache集合
   * </p>
   */

  public void rollback() {
    // 解锁丢失的缓存数据，使其根据{@link #entriesMissedInCache}删除二级缓存的数据
    unlockMissedEntries();
    // 重置clearOnCommit为false，清空entriesToAddOnCommit、entriesMissedInCache集合
    reset();
  }

  /**
   * 重置一级缓存
   * <p>
   *    {@link #entriesToAddOnCommit}和{@link #entriesMissedInCache}全清空，而不清空二级缓存。
   *        并将{@link #clearOnCommit}标记设置为false
   * </p>
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 将数据刷新到二级缓存
   * <p>
   *     将entriesToAddOnCommit和entriesMissedInCache添加到二级缓存,entriesMissedInCache只存储了key，所以添加到二级缓存时，value为null
   * </p>
   */
  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 解锁丢失的缓存数据
   * <p>
   *     删除在二级缓存中关于{@link #entriesMissedInCache}的所有元素的缓存数据，这里捕捉了所有移除缓存时的异常，保证每个缓存都能删除
   * </p>
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
