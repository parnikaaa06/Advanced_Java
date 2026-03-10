
import java.util.*;

public class MultiLevelCacheSystemwithHashTables {
    static class VideoData {
        String id, content;
        VideoData(String id, String content) { this.id = id; this.content = content; }
    }

    static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int capacity;
        LRUCache(int capacity) { super(16, 0.75f, true); this.capacity = capacity; }
        protected boolean removeEldestEntry(Map.Entry<K, V> e) { return size() > capacity; }
    }

    static class LevelStats {
        long hits, misses, totalNs;
        void hit(long ns) { hits++; totalNs += ns; }
        void miss() { misses++; }
        double hitRate() { long t = hits + misses; return t == 0 ? 0 : (100.0 * hits / t); }
        double avgMs() { return hits == 0 ? 0 : (totalNs / 1_000_000.0 / hits); }
    }

    static class MultiLevelCache {
        private final LRUCache<String, VideoData> l1 = new LRUCache<>(10_000);
        private final LRUCache<String, VideoData> l2 = new LRUCache<>(100_000); // SSD-backed simulated
        private final Map<String, VideoData> l3db = new HashMap<>();
        private final Map<String, Integer> accessCount = new HashMap<>();
        private final int promoteThreshold;

        private final LevelStats s1 = new LevelStats(), s2 = new LevelStats(), s3 = new LevelStats();
        private final double L1_MS = 0.5, L2_MS = 5.0, L3_MS = 150.0;

        MultiLevelCache(int promoteThreshold) { this.promoteThreshold = promoteThreshold; }

        void seedDatabase(VideoData... videos) {
            for (VideoData v : videos) l3db.put(v.id, v);
        }

        VideoData getVideo(String id) {
            long ns;
            ns = (long)(L1_MS * 1_000_000);
            if (l1.containsKey(id)) { s1.hit(ns); touch(id); return l1.get(id); }
            s1.miss();

            ns = (long)(L2_MS * 1_000_000);
            if (l2.containsKey(id)) {
                s2.hit(ns);
                VideoData v = l2.get(id);
                int c = touch(id);
                if (c >= promoteThreshold) l1.put(id, v); // L2 -> L1 promotion
                return v;
            }
            s2.miss();

            ns = (long)(L3_MS * 1_000_000);
            VideoData v = l3db.get(id);
            if (v != null) {
                s3.hit(ns);
                int c = touch(id);
                l2.put(id, v);                // L3 -> L2
                if (c >= promoteThreshold) l1.put(id, v); // optional direct promotion after repeated requests
            } else {
                s3.miss();
            }
            return v;
        }

        void updateContent(String id, String newContent) {
            VideoData nv = new VideoData(id, newContent);
            l3db.put(id, nv);
            l1.remove(id); // invalidate stale copies
            l2.remove(id);
            accessCount.remove(id);
        }

        private int touch(String id) {
            int c = accessCount.getOrDefault(id, 0) + 1;
            accessCount.put(id, c);
            return c;
        }

        String getStatistics() {
            long totalHits = s1.hits + s2.hits + s3.hits;
            long totalReqs = s1.hits + s1.misses; // requests counted at L1 entry
            double overallHit = totalReqs == 0 ? 0 : (100.0 * totalHits / totalReqs);
            double avgMs = totalReqs == 0 ? 0 :
                    ((s1.totalNs + s2.totalNs + s3.totalNs) / 1_000_000.0 / totalReqs);

            return String.format(
                    "L1: Hit Rate %.2f%%, Avg Time %.2fms\n" +
                            "L2: Hit Rate %.2f%%, Avg Time %.2fms\n" +
                            "L3: Hit Rate %.2f%%, Avg Time %.2fms\n" +
                            "Overall: Hit Rate %.2f%%, Avg Time %.2fms",
                    s1.hitRate(), s1.avgMs(), s2.hitRate(), s2.avgMs(), s3.hitRate(), s3.avgMs(), overallHit, avgMs
            );
        }
    }

    public static void main(String[] args) {
        MultiLevelCache cache = new MultiLevelCache(2); // promote on 2nd access
        cache.seedDatabase(
                new VideoData("video_123", "Movie-A"),
                new VideoData("video_999", "Movie-B"),
                new VideoData("video_777", "Movie-C")
        );

        cache.getVideo("video_123"); // L3 hit -> L2 add
        cache.getVideo("video_123"); // L2 hit -> promoted to L1
        cache.getVideo("video_123"); // L1 hit
        cache.getVideo("video_999"); // L3 hit -> L2 add

        cache.updateContent("video_123", "Movie-A-Updated"); // invalidates L1/L2
        cache.getVideo("video_123"); // fetch fresh from L3

        System.out.println(cache.getStatistics());
    }
}
