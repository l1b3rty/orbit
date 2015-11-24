package com.ea.orbit.actors.test;

import com.ea.orbit.concurrent.Task;
import com.ea.orbit.tuples.Pair;

import javax.inject.Singleton;

import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Used for testing
 */
@Singleton
public class FakeSync
{
    private LoadingMap<Object, Task> tasks = new LoadingMap<>(Task::new);

    private LoadingMap<String, Semaphore> semaphores = new LoadingMap<>(() -> new Semaphore(0));

    private LoadingMap<String, Deque> deques = new LoadingMap<>(ConcurrentLinkedDeque::new);

    private LoadingMap<String, CompletableFuture> futures = new LoadingMap<>(CompletableFuture::new);


    // pairs of completable futures and the future completions.
    private Queue<Pair<CompletableFuture, Object>> blockedFutures = new ConcurrentLinkedQueue<>();

    // just calls a function
    public <T> Task<T> futureFrom(Supplier<Task<T>> supplier)
    {
        return supplier.get();
    }

    /**
     * Creates and an uncompleted future and adds it the the queue for later completion.
     * To help with the tests
     */
    public <T> CompletableFuture<T> getBlockedFuture(T value)
    {
        final CompletableFuture<T> future = new CompletableFuture<>();
        blockedFutures.add(Pair.of(future, value));
        return future;
    }

    public <T> CompletableFuture<T> getBlockedFuture()
    {
        return getBlockedFuture(null);
    }

    public <T> Task<T> getBlockedTask(T value)
    {
        final Task<T> future = new Task<>();
        blockedFutures.add(Pair.of(future, value));
        return future;
    }


    public <T> Task<T> getBlockedTask()
    {
        return getBlockedTask(null);
    }

    /**
     * Complete all the blocked futures, even new ones created while executing this method
     */
    public void completeFutures()
    {
        while (blockedFutures.size() > 0)
        {
            final Pair<CompletableFuture, Object> pair = blockedFutures.poll();
            if (pair != null)
            {
                pair.getLeft().complete(pair.getRight());
            }
        }
    }

    public int blockedFutureCount()
    {
        return blockedFutures.size();
    }

    @Deprecated
    public void put(Object key, Object value)
    {
        get(key).complete(value);
    }

    @Deprecated
    public <T> Task<T> get(Object key)
    {
        Task<?> t = tasks.get(key);
        if (t == null)
        {
            tasks.putIfAbsent(key, new Task<>());
            t = tasks.get(key);
        }
        //noinspection unchecked
        return (Task<T>) t;
    }


    public Semaphore semaphore(String semaphoreName)
    {
        return semaphores.getOrAdd(semaphoreName);
    }

    public <T> Task<T> task(String name)
    {
        return tasks.getOrAdd(name);
    }

    public <T> CompletableFuture<T> future(String name)
    {
        return futures.getOrAdd(name);
    }

    public <T> Deque<T> deque(String name)
    {
        return deques.getOrAdd(name);
    }

    private static class LoadingMap<K, V> extends ConcurrentHashMap<K, V>
    {
        private Supplier<V> supplier;

        public LoadingMap(Supplier<V> supplier)
        {
            this.supplier = supplier;
        }

        public V getOrAdd(K key)
        {
            V value = get(key);
            if (value == null)
            {
                final V newValue = supplier.get();
                V oldValue = putIfAbsent(key, newValue);
                return oldValue != null ? oldValue : newValue;
            }
            return value;
        }
    }
}
