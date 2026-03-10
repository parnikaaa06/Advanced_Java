import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class DNSCacheDemo {

    static class DNSEntry {
        String ipAddress;
        long timestamp;
        long expiryTime;

        DNSEntry(String ipAddress, int ttlSeconds) {
            this.timestamp = System.currentTimeMillis();
            this.expiryTime = this.timestamp + ttlSeconds * 1000L;
            this.ipAddress = ipAddress;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }
    }

    static class DNSCache {
        private final int capacity;
        private final HashMap<String, DNSEntry> table = new HashMap<>();
        private final LinkedHashMap<String, Boolean> lru = new LinkedHashMap<>(16, 0.75f, true);

        private long hits = 0, misses = 0, totalLookups = 0;
        private double totalLookupMs = 0;
        private volatile boolean running = true;

        DNSCache(int capacity, int cleanupIntervalSeconds) {
            this.capacity = capacity;

            Thread cleaner = new Thread(() -> {
                while (running) {
                    removeExpiredEntries();
                    try {
                        Thread.sleep(cleanupIntervalSeconds * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            cleaner.setDaemon(true);
            cleaner.start();
        }

        private String queryUpstream(String domain) throws Exception {
            return InetAddress.getByName(domain).getHostAddress();
        }

        private void evictIfNeeded() {
            while (table.size() > capacity) {
                Iterator<String> it = lru.keySet().iterator();
                if (!it.hasNext()) return;
                String leastRecentlyUsed = it.next();
                it.remove();
                table.remove(leastRecentlyUsed);
            }
        }

        public synchronized String resolve(String domain, int ttlSeconds) throws Exception {
            long start = System.nanoTime();
            totalLookups++;

            DNSEntry entry = table.get(domain);
            if (entry != null && !entry.isExpired()) {
                hits++;
                lru.get(domain);
                totalLookupMs += (System.nanoTime() - start) / 1_000_000.0;
                return entry.ipAddress;
            }

            if (entry != null && entry.isExpired()) {
                table.remove(domain);
                lru.remove(domain);
            }

            misses++;
            String ip = queryUpstream(domain);
            table.put(domain, new DNSEntry(ip, ttlSeconds));
            lru.put(domain, true);
            evictIfNeeded();

            totalLookupMs += (System.nanoTime() - start) / 1_000_000.0;
            return ip;
        }

        public synchronized void removeExpiredEntries() {
            Iterator<Map.Entry<String, DNSEntry>> it = table.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, DNSEntry> item = it.next();
                if (item.getValue().isExpired()) {
                    lru.remove(item.getKey());
                    it.remove();
                }
            }
        }

        public synchronized String getCacheStats() {
            long total = hits + misses;
            double hitRate = total == 0 ? 0 : (hits * 100.0 / total);
            double missRate = total == 0 ? 0 : (misses * 100.0 / total);
            double avgLookup = totalLookups == 0 ? 0 : (totalLookupMs / totalLookups);
            return String.format("Hit Rate: %.2f%%, Miss Rate: %.2f%%, Avg Lookup Time: %.3fms",
                    hitRate, missRate, avgLookup);
        }

        public void stop() {
            running = false;
        }
    }

    public static void main(String[] args) throws Exception {
        DNSCache cache = new DNSCache(3, 1);
        String domain = "google.com";
        int ttl = 3;

        String ip1 = cache.resolve(domain, ttl);
        System.out.println("resolve(\"google.com\") -> Cache MISS -> " + ip1 + " (TTL: 3s)");

        String ip2 = cache.resolve(domain, ttl);
        System.out.println("resolve(\"google.com\") -> Cache HIT -> " + ip2);

        Thread.sleep(4000);

        String ip3 = cache.resolve(domain, ttl);
        System.out.println("resolve(\"google.com\") -> Cache EXPIRED -> Query upstream -> " + ip3);

        System.out.println("getCacheStats() -> " + cache.getCacheStats());
        cache.stop();
    }
}