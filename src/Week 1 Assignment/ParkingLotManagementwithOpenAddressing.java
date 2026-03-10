import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

public class ParkingLotManagementwithOpenAddressing {
    enum SpotStatus { EMPTY, OCCUPIED, DELETED }

    static class Spot {
        SpotStatus status = SpotStatus.EMPTY;
        String plate;
        LocalDateTime entryTime;
    }

    static class ParkingLot {
        private final Spot[] table;
        private final int capacity;
        private int occupiedCount;

        private long totalProbes;
        private long totalParkingOps;

        private long occupancySampleSum;
        private long occupancySampleCount;

        private final int[] hourlyEntries = new int[24];

        ParkingLot(int capacity) {
            this.capacity = capacity;
            this.table = new Spot[capacity];
            for (int i = 0; i < capacity; i++) table[i] = new Spot();
        }

        private int hash(String plate) {
            int h = 0;
            for (char c : plate.toCharArray()) h = (31 * h + c) & 0x7fffffff;
            return h % capacity;
        }

        public String parkVehicle(String plate) {
            if (occupiedCount == capacity) return "Parking full";

            int preferred = hash(plate);
            int firstDeleted = -1;

            for (int probes = 0; probes < capacity; probes++) {
                int idx = (preferred + probes) % capacity;
                Spot s = table[idx];

                if (s.status == SpotStatus.OCCUPIED && plate.equals(s.plate)) {
                    return "Vehicle already parked at spot #" + idx;
                }

                if (s.status == SpotStatus.DELETED && firstDeleted == -1) firstDeleted = idx;

                if (s.status == SpotStatus.EMPTY) {
                    int target = (firstDeleted != -1) ? firstDeleted : idx;
                    Spot t = table[target];
                    t.status = SpotStatus.OCCUPIED;
                    t.plate = plate;
                    t.entryTime = LocalDateTime.now();
                    occupiedCount++;

                    totalProbes += probes;
                    totalParkingOps++;
                    occupancySampleSum += occupiedCount;
                    occupancySampleCount++;
                    hourlyEntries[t.entryTime.getHour()]++;

                    return "Assigned spot #" + target + " (" + probes + " probes)";
                }
            }

            if (firstDeleted != -1) {
                Spot t = table[firstDeleted];
                t.status = SpotStatus.OCCUPIED;
                t.plate = plate;
                t.entryTime = LocalDateTime.now();
                occupiedCount++;

                totalProbes += capacity - 1;
                totalParkingOps++;
                occupancySampleSum += occupiedCount;
                occupancySampleCount++;
                hourlyEntries[t.entryTime.getHour()]++;

                return "Assigned spot #" + firstDeleted + " (" + (capacity - 1) + " probes)";
            }

            return "Parking full";
        }

        public String exitVehicle(String plate) {
            int preferred = hash(plate);

            for (int probes = 0; probes < capacity; probes++) {
                int idx = (preferred + probes) % capacity;
                Spot s = table[idx];

                if (s.status == SpotStatus.EMPTY) break;

                if (s.status == SpotStatus.OCCUPIED && plate.equals(s.plate)) {
                    LocalDateTime now = LocalDateTime.now();
                    Duration d = Duration.between(s.entryTime, now);
                    double hours = Math.max(1.0, d.toMinutes() / 60.0);
                    double fee = hours * 5.5;

                    s.status = SpotStatus.DELETED;
                    s.plate = null;
                    s.entryTime = null;
                    occupiedCount--;

                    occupancySampleSum += occupiedCount;
                    occupancySampleCount++;

                    long h = d.toHours();
                    long m = d.toMinutesPart();
                    return String.format(Locale.US,
                            "Spot #%d freed, Duration: %dh %02dm, Fee: $%.2f",
                            idx, h, m, fee);
                }
            }
            return "Vehicle not found";
        }

        public int findNearestAvailableSpotToEntrance() {
            for (int i = 0; i < capacity; i++) {
                if (table[i].status != SpotStatus.OCCUPIED) return i;
            }
            return -1;
        }

        public double loadFactor() {
            return occupiedCount / (double) capacity;
        }

        public String getStatistics() {
            double occupancyPct = loadFactor() * 100.0;
            double avgProbes = totalParkingOps == 0 ? 0.0 : (double) totalProbes / totalParkingOps;
            double avgOccupancy = occupancySampleCount == 0 ? 0.0 : (double) occupancySampleSum / occupancySampleCount;

            int peakHour = 0;
            for (int h = 1; h < 24; h++) {
                if (hourlyEntries[h] > hourlyEntries[peakHour]) peakHour = h;
            }

            return String.format(Locale.US,
                    "Occupancy: %.1f%%, Avg Occupancy: %.1f spots, Avg Probes: %.2f, Peak Hour: %d-%d",
                    occupancyPct, avgOccupancy, avgProbes, peakHour, (peakHour + 1) % 24);
        }
    }

    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot(500);

        System.out.println(lot.parkVehicle("ABC-1234"));
        System.out.println(lot.parkVehicle("ABC-1235"));
        System.out.println(lot.parkVehicle("XYZ-9999"));

        System.out.println("Nearest available spot: #" + lot.findNearestAvailableSpotToEntrance());

        System.out.println(lot.exitVehicle("ABC-1234"));
        System.out.println(lot.getStatistics());
    }
}
