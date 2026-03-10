import java.util.*;

public class Plagiarism_Detection_System {

    static class PlagiarismDetector {
        private final int n;
        private final Map<String, Set<String>> nGramIndex = new HashMap<>(); // n-gram -> docIds
        private final Map<String, List<String>> docNGrams = new HashMap<>();  // docId -> n-grams

        PlagiarismDetector(int n) {
            this.n = n;
        }

        public void addDocument(String docId, String text) {
            List<String> grams = extractNGrams(text, n);
            docNGrams.put(docId, grams);
            for (String gram : new HashSet<>(grams)) {
                nGramIndex.computeIfAbsent(gram, k -> new HashSet<>()).add(docId);
            }
        }

        public AnalysisResult analyzeDocument(String queryId, String queryText, int topK) {
            List<String> queryNGrams = extractNGrams(queryText, n);
            Set<String> uniqueQuery = new HashSet<>(queryNGrams);

            Map<String, Integer> matchCount = new HashMap<>();
            for (String gram : uniqueQuery) {
                Set<String> docs = nGramIndex.get(gram);
                if (docs == null) continue;
                for (String docId : docs) {
                    if (!docId.equals(queryId)) {
                        matchCount.put(docId, matchCount.getOrDefault(docId, 0) + 1);
                    }
                }
            }

            List<Match> matches = new ArrayList<>();
            int total = Math.max(1, uniqueQuery.size());
            for (Map.Entry<String, Integer> e : matchCount.entrySet()) {
                double similarity = (e.getValue() * 100.0) / total;
                matches.add(new Match(e.getKey(), e.getValue(), similarity));
            }

            matches.sort((a, b) -> Double.compare(b.similarityPercent, a.similarityPercent));
            if (matches.size() > topK) matches = matches.subList(0, topK);

            return new AnalysisResult(queryNGrams.size(), matches);
        }

        private static List<String> extractNGrams(String text, int n) {
            String[] words = text.toLowerCase()
                    .replaceAll("[^a-z0-9\\s]", " ")
                    .trim()
                    .split("\\s+");

            List<String> grams = new ArrayList<>();
            if (words.length < n || words[0].isEmpty()) return grams;

            for (int i = 0; i <= words.length - n; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    if (j > 0) sb.append(' ');
                    sb.append(words[i + j]);
                }
                grams.add(sb.toString());
            }
            return grams;
        }
    }

    static class Match {
        String docId;
        int matchingNGrams;
        double similarityPercent;

        Match(String docId, int matchingNGrams, double similarityPercent) {
            this.docId = docId;
            this.matchingNGrams = matchingNGrams;
            this.similarityPercent = similarityPercent;
        }
    }

    static class AnalysisResult {
        int extractedNGrams;
        List<Match> topMatches;

        AnalysisResult(int extractedNGrams, List<Match> topMatches) {
            this.extractedNGrams = extractedNGrams;
            this.topMatches = topMatches;
        }
    }

    public static void main(String[] args) {
        PlagiarismDetector detector = new PlagiarismDetector(5); // use 5-grams

        detector.addDocument("essay_089.txt",
                "Machine learning enables systems to learn from data and improve over time with better predictions.");

        detector.addDocument("essay_092.txt",
                "Machine learning enables systems to learn from data and improve over time with better predictions. " +
                        "It is widely used in healthcare finance and education for data driven decisions.");

        detector.addDocument("essay_101.txt",
                "Ancient literature explores philosophy ethics and politics through dramatic dialogues and narratives.");

        String queryText = "Machine learning enables systems to learn from data and improve over time with better predictions. " +
                "It is used in education for data driven decisions.";

        AnalysisResult result = detector.analyzeDocument("essay_123.txt", queryText, 3);

        System.out.println("analyzeDocument(\"essay_123.txt\")");
        System.out.println("-> Extracted " + result.extractedNGrams + " n-grams");

        for (Match m : result.topMatches) {
            String flag = m.similarityPercent >= 60 ? " (PLAGIARISM DETECTED)"
                    : (m.similarityPercent >= 15 ? " (suspicious)" : "");
            System.out.printf("-> Found %d matching n-grams with \"%s\"%n", m.matchingNGrams, m.docId);
            System.out.printf("-> Similarity: %.1f%%%s%n", m.similarityPercent, flag);
        }
    }
}