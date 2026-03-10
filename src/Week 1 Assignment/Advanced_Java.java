import java.util.*;

public class Advanced_Java {

    private HashMap<String, Integer> userDatabase;

    private HashMap<String, Integer> attemptFrequency;

    public Advanced_Java() {
        userDatabase = new HashMap<>();
        attemptFrequency = new HashMap<>();
    }

    public void addUser(String username, int userId) {
        userDatabase.put(username.toLowerCase(), userId);
    }

    public boolean checkAvailability(String username) {
        username = username.toLowerCase();

        attemptFrequency.put(username,
                attemptFrequency.getOrDefault(username, 0) + 1);

        return !userDatabase.containsKey(username);
    }

    public List<String> suggestAlternatives(String username) {
        List<String> suggestions = new ArrayList<>();

        if (checkAvailability(username)) {
            suggestions.add(username);
            return suggestions;
        }

        for (int i = 1; i <= 5; i++) {
            String suggestion = username + i;
            if (!userDatabase.containsKey(suggestion)) {
                suggestions.add(suggestion);
            }
        }

        if (username.contains("_")) {
            String alt = username.replace("_", ".");
            if (!userDatabase.containsKey(alt)) {
                suggestions.add(alt);
            }
        }

        return suggestions;
    }

    public String getMostAttempted() {
        String mostAttempted = "";
        int max = 0;

        for (Map.Entry<String, Integer> entry : attemptFrequency.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                mostAttempted = entry.getKey();
            }
        }

        return mostAttempted + " (" + max + " attempts)";
    }

    public static void main(String[] args) {

        Advanced_Java system = new Advanced_Java();

        system.addUser("john_doe", 1);
        system.addUser("admin", 2);
        system.addUser("alex", 3);

        System.out.println("Check john_doe: " +
                system.checkAvailability("john_doe"));

        System.out.println("Check jane_smith: " +
                system.checkAvailability("jane_smith"));

        System.out.println("Suggestions for john_doe: " +
                system.suggestAlternatives("john_doe"));

        for(int i=0;i<5;i++) system.checkAvailability("admin");
        for(int i=0;i<3;i++) system.checkAvailability("john_doe");

        System.out.println("Most attempted username: " +
                system.getMostAttempted());
    }
}