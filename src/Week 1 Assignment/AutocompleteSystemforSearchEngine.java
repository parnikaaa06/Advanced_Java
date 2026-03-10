
import java.util.*;

public class AutocompleteSystemforSearchEngine {
    static class AutocompleteSystem {
        private static final int TOP_K = 10;

        static class TrieNode {
            Map<Character, TrieNode> next = new HashMap<>();
            List<String> top = new ArrayList<>();
        }

        private final TrieNode root = new TrieNode();
        private final Map<String, Integer> freq = new HashMap<>();

        public void updateFrequency(String query) {
            query = normalize(query);
            if (query.isEmpty()) return;

            freq.put(query, freq.getOrDefault(query, 0) + 1);
            TrieNode node = root;
            refreshTop(node, query);
            for (char ch : query.toCharArray()) {
                node = node.next.computeIfAbsent(ch, c -> new TrieNode());
                refreshTop(node, query);
            }
        }

        public List<String> search(String prefix) {
            prefix = normalize(prefix);
            Set<String> candidates = new HashSet<>(topForPrefix(prefix));

            if (candidates.size() < TOP_K) {
                Set<String> fuzzy = new HashSet<>();
                collectFuzzy(root, prefix, 0, 1, fuzzy); // typo tolerance: edit distance <= 1
                candidates.addAll(fuzzy);
            }

            List<String> out = new ArrayList<>(candidates);
            out.sort((a, b) -> {
                int fa = freq.getOrDefault(a, 0), fb = freq.getOrDefault(b, 0);
                if (fa != fb) return Integer.compare(fb, fa);
                return a.compareTo(b);
            });
            return out.size() > TOP_K ? out.subList(0, TOP_K) : out;
        }

        private List<String> topForPrefix(String prefix) {
            TrieNode node = root;
            for (char ch : prefix.toCharArray()) {
                node = node.next.get(ch);
                if (node == null) return Collections.emptyList();
            }
            return node.top;
        }

        private void refreshTop(TrieNode node, String query) {
            Set<String> set = new HashSet<>(node.top);
            set.add(query);
            List<String> list = new ArrayList<>(set);
            list.sort((a, b) -> {
                int fa = freq.getOrDefault(a, 0), fb = freq.getOrDefault(b, 0);
                if (fa != fb) return Integer.compare(fb, fa);
                return a.compareTo(b);
            });
            if (list.size() > TOP_K) list = list.subList(0, TOP_K);
            node.top = list;
        }

        private void collectFuzzy(TrieNode node, String prefix, int i, int editsLeft, Set<String> out) {
            if (node == null || editsLeft < 0) return;
            if (i == prefix.length()) {
                out.addAll(node.top);
                if (editsLeft > 0) { // insertion at end
                    for (TrieNode child : node.next.values()) collectFuzzy(child, prefix, i, editsLeft - 1, out);
                }
                return;
            }

            char want = prefix.charAt(i);

            // delete current char from prefix
            if (editsLeft > 0) collectFuzzy(node, prefix, i + 1, editsLeft - 1, out);

            for (Map.Entry<Character, TrieNode> e : node.next.entrySet()) {
                char got = e.getKey();
                TrieNode child = e.getValue();

                if (got == want) {
                    collectFuzzy(child, prefix, i + 1, editsLeft, out); // match
                } else if (editsLeft > 0) {
                    collectFuzzy(child, prefix, i + 1, editsLeft - 1, out); // substitute
                }

                if (editsLeft > 0) collectFuzzy(child, prefix, i, editsLeft - 1, out); // insert extra char
            }
        }

        private String normalize(String s) {
            return s == null ? "" : s.trim().toLowerCase();
        }
    }

    public static void main(String[] args) {
        AutocompleteSystem ac = new AutocompleteSystem();

        seed(ac, "java tutorial", 1234567);
        seed(ac, "javascript", 987654);
        seed(ac, "java download", 456789);
        seed(ac, "java 21 features", 1);
        seed(ac, "java spring boot", 350000);
        seed(ac, "java interview questions", 250000);

        System.out.println("search(\"jav\"):");
        List<String> s1 = ac.search("jav");
        for (int i = 0; i < s1.size(); i++) {
            String q = s1.get(i);
            System.out.printf("%d. %s (%d searches)%n", i + 1, q, ac.freq.get(q));
        }

        ac.updateFrequency("java 21 features");
        ac.updateFrequency("java 21 features");
        System.out.println("\nupdateFrequency(\"java 21 features\") -> " + ac.freq.get("java 21 features"));

        System.out.println("\nsearch(\"jvaa\") [typo]:");
        List<String> s2 = ac.search("jvaa");
        for (int i = 0; i < s2.size(); i++) {
            String q = s2.get(i);
            System.out.printf("%d. %s (%d searches)%n", i + 1, q, ac.freq.get(q));
        }
    }

    private static void seed(AutocompleteSystem ac, String q, int count) {
        for (int i = 0; i < count; i++) ac.updateFrequency(q);
    }
}
