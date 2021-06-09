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
package org.apache.ibatis.cache.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * 永久缓存 Cache接口实现类,里面就是维护着一个HashMap
 * <p>
 *     Cache接口只有这唯一一个基础实现，其他实现类全都是装饰模式持有另一个缓存对象
 * </p>
 * @author Clinton Begin
 */
public class PerpetualCache implements Cache {

  /** 缓存对象的唯一标识 */
  private final String id;

  /** 对象内部维护的HashMap,缓存Map */
  private final Map<Object, Object> cache = new HashMap<>();

  public PerpetualCache(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public int getSize() {
    return cache.size();
  }

  @Override
  public void putObject(Object key, Object value) {
    cache.put(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return cache.get(key);
  }

  @Override
  public Object removeObject(Object key) {
    return cache.remove(key);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  /**
   * 通过判断ID去实现相等,如果当前类型对象的ID属性为null会抛出{@link CacheException}
   * @param o
   * @return
   */
  @Override
  public boolean equals(Object o) {
    if (getId() == null) {
      throw new CacheException("Cache instances require an ID.");
    }
    // 判断地址
    if (this == o) {
      return true;
    }
    // 非Cache的子类
    if (!(o instanceof Cache)) {
      return false;
    }

    Cache otherCache = (Cache) o;
    // 判断ID
    return getId().equals(otherCache.getId());
  }

  /**
   * 把属性ID的hashCode作为本类对象的hashCode，如果当前类型对象的ID属性为null会抛出{@link CacheException}
   * @return
   */
  @Override
  public int hashCode() {
    if (getId() == null) {
      throw new CacheException("Cache instances require an ID.");
    }
    return getId().hashCode();
  }

}
