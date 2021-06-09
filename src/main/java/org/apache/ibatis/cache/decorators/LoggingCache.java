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

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 日志功能，装饰类，用于记录缓存的命中率，如果开启了DEBUG模式，则会输出命中率日志。
 * @author Clinton Begin
 */
public class LoggingCache implements Cache {

  /** log的名字一般是命名空间，从{@link #delegate}的{@link Cache#getId()}方法获取。 */
  private final Log log;
  private final Cache delegate;
  /** 请求次数 */
  protected int requests = 0;
  /** 命中次数 */
  protected int hits = 0;

  public LoggingCache(Cache delegate) {
    this.delegate = delegate;
    this.log = LogFactory.getLog(getId());
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object object) {
    delegate.putObject(key, object);
  }

  /**
   * 获取缓存数据，并统计请求次数和命中次数
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // 每次获取，请求次数+1
    requests++;
    final Object value = delegate.getObject(key);
    if (value != null) {
      // 获取的缓存数据时存在的，命中次数+1
      hits++;
    }
    if (log.isDebugEnabled()) {
      log.debug("Cache Hit Ratio [" + getId() + "]: " + getHitRatio());
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  /**
   * 获取命中率
   * <p>
   *     命中率 = 命中次数 / 请求次数
   * </p>
   * @return
   */
  private double getHitRatio() {
    return (double) hits / (double) requests;
  }

}
