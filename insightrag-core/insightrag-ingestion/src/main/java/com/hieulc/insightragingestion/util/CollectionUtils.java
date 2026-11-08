package com.hieulc.insightragingestion.util;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

public final class CollectionUtils {

  private CollectionUtils() {}

  public static <T> List<List<T>> partition(List<T> list, int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("Batch size must be greater than 0");
    }

    if (list == null || list.isEmpty()) {
      return Collections.emptyList();
    }

    return (list instanceof RandomAccess)
        ? new RandomAccessPartition<>(list, size)
        : new Partition<>(list, size);
  }

  private static class Partition<T> extends AbstractList<List<T>> {
    final List<T> list;
    final int size;

    Partition(List<T> list, int size) {
      this.list = list;
      this.size = size;
    }

    @Override
    public List<T> get(int index) {
      int partitionSize = size();
      if (index < 0 || index >= partitionSize) {
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + partitionSize);
      }
      int start = index * size;
      int end = Math.min(start + size, list.size());
      return list.subList(start, end);
    }

    @Override
    public int size() {
      return (list.size() + size - 1) / size;
    }

    @Override
    public boolean isEmpty() {
      return list.isEmpty();
    }
  }

  private static final class RandomAccessPartition<T> extends Partition<T> implements RandomAccess {
    RandomAccessPartition(List<T> list, int size) {
      super(list, size);
    }
  }
}
