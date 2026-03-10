
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

public class TwoSumProblemVariantsforFinancialTransactions {
    static class Transaction {
        int id;
        int amount;
        String merchant;
        String account;
        LocalTime time;

        Transaction(int id, int amount, String merchant, String account, String time) {
            this.id = id;
            this.amount = amount;
            this.merchant = merchant;
            this.account = account;
            this.time = LocalTime.parse(time);
        }
    }

    static List<int[]> findTwoSum(List<Transaction> txns, int target) {
        Map<Integer, List<Integer>> seen = new HashMap<>();
        List<int[]> ans = new ArrayList<>();
        for (Transaction t : txns) {
            int need = target - t.amount;
            for (int otherId : seen.getOrDefault(need, Collections.emptyList())) {
                ans.add(new int[]{otherId, t.id});
            }
            seen.computeIfAbsent(t.amount, k -> new ArrayList<>()).add(t.id);
        }
        return ans;
    }

    static List<int[]> findTwoSumWithinHour(List<Transaction> txns, int target) {
        List<Transaction> sorted = new ArrayList<>(txns);
        sorted.sort(Comparator.comparing(a -> a.time));
        Map<Integer, Deque<Transaction>> window = new HashMap<>();
        List<int[]> ans = new ArrayList<>();

        for (Transaction cur : sorted) {
            for (Deque<Transaction> q : window.values()) {
                while (!q.isEmpty() && Duration.between(q.peekFirst().time, cur.time).toMinutes() > 60) {
                    q.pollFirst();
                }
            }
            int need = target - cur.amount;
            for (Transaction t : window.getOrDefault(need, new ArrayDeque<>())) {
                ans.add(new int[]{t.id, cur.id});
            }
            window.computeIfAbsent(cur.amount, k -> new ArrayDeque<>()).addLast(cur);
        }
        return ans;
    }

    static List<List<Integer>> findKSum(List<Transaction> txns, int k, int target) {
        List<List<Integer>> ans = new ArrayList<>();
        backtrack(txns, 0, k, target, new ArrayList<>(), ans);
        return ans;
    }

    static void backtrack(List<Transaction> txns, int start, int k, int target, List<Integer> path, List<List<Integer>> ans) {
        if (k == 0) {
            if (target == 0) ans.add(new ArrayList<>(path));
            return;
        }
        for (int i = start; i <= txns.size() - k; i++) {
            path.add(txns.get(i).id);
            backtrack(txns, i + 1, k - 1, target - txns.get(i).amount, path, ans);
            path.remove(path.size() - 1);
        }
    }

    static class DuplicateGroup {
        int amount;
        String merchant;
        Set<String> accounts = new HashSet<>();

        DuplicateGroup(int amount, String merchant) {
            this.amount = amount;
            this.merchant = merchant;
        }

        @Override
        public String toString() {
            return "{amount:" + amount + ", merchant:\"" + merchant + "\", accounts:" + accounts + "}";
        }
    }

    static List<DuplicateGroup> detectDuplicates(List<Transaction> txns) {
        Map<String, DuplicateGroup> map = new HashMap<>();
        for (Transaction t : txns) {
            String key = t.amount + "|" + t.merchant;
            map.computeIfAbsent(key, k -> new DuplicateGroup(t.amount, t.merchant)).accounts.add(t.account);
        }
        List<DuplicateGroup> ans = new ArrayList<>();
        for (DuplicateGroup g : map.values()) {
            if (g.accounts.size() > 1) ans.add(g);
        }
        return ans;
    }

    public static void main(String[] args) {
        List<Transaction> transactions = List.of(
                new Transaction(1, 500, "Store A", "acc1", "10:00"),
                new Transaction(2, 300, "Store B", "acc2", "10:15"),
                new Transaction(3, 200, "Store C", "acc3", "10:30"),
                new Transaction(4, 500, "Store A", "acc4", "10:45")
        );

        System.out.println("findTwoSum(target=500):");
        for (int[] p : findTwoSum(transactions, 500)) {
            System.out.println("(" + p[0] + ", " + p[1] + ")");
        }

        System.out.println("\nfindTwoSumWithinHour(target=700):");
        for (int[] p : findTwoSumWithinHour(transactions, 700)) {
            System.out.println("(" + p[0] + ", " + p[1] + ")");
        }

        System.out.println("\nfindKSum(k=3, target=1000): " + findKSum(transactions, 3, 1000));
        System.out.println("detectDuplicates(): " + detectDuplicates(transactions));
    }
}
