package com.kama.jchatmind.support.cache;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TtlCache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> entries = new ConcurrentHashMap<>();

    public V get(K key) {
        long now = System.currentTimeMillis();
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(now)) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    public V getOrLoad(K key, Duration ttl, Supplier<V> loader) {
        V cached = get(key);
        if (cached != null) {
            return cached;
        }

        V loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public void put(K key, V value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            entries.remove(key);
            return;
        }

        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        entries.put(key, new CacheEntry<>(value, ttl, expiresAt));
    }

    public void invalidate(K key) {
        entries.remove(key);
    }

    public void invalidateIf(Predicate<K> predicate) {
        entries.keySet().removeIf(predicate);
    }

    public void forEachValid(BiConsumer<K, CacheSnapshot<V>> consumer) {
        long now = System.currentTimeMillis();
        entries.forEach((key, entry) -> {
            if (entry.isExpired(now)) {
                entries.remove(key, entry);
                return;
            }
            consumer.accept(key, new CacheSnapshot<>(entry.value(), entry.ttl()));
        });
    }

    public record CacheSnapshot<V>(V value, Duration ttl) {
    }

    private record CacheEntry<V>(V value, Duration ttl, long expiresAt) {
        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
