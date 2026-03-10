import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class FlashSaleInventoryManager {

    public static final class PurchaseResult {
        private final boolean success;
        private final String message;
        private final int remainingStock;
        private final int waitingPosition;

        public PurchaseResult(boolean success, String message, int remainingStock, int waitingPosition) {
            this.success = success;
            this.message = message;
            this.remainingStock = remainingStock;
            this.waitingPosition = waitingPosition;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getRemainingStock() {
            return remainingStock;
        }

        public int getWaitingPosition() {
            return waitingPosition;
        }

        @Override
        public String toString() {
            if (success) {
                return message + ", " + remainingStock + " units remaining";
            }
            return message + ", position #" + waitingPosition;
        }
    }

    private static final class ProductInventory {
        private final AtomicInteger stock;
        private final LinkedHashMap<Long, Integer> waitingList;
        private final AtomicInteger nextWaitingPosition;
        private final ReentrantLock waitingListLock;

        private ProductInventory(int initialStock) {
            this.stock = new AtomicInteger(initialStock);
            this.waitingList = new LinkedHashMap<>();
            this.nextWaitingPosition = new AtomicInteger(1);
            this.waitingListLock = new ReentrantLock();
        }

        private int checkStock() {
            return stock.get();
        }

        private PurchaseResult purchase(long userId) {
            while (true) {
                int current = stock.get();
                if (current <= 0) {
                    int pos = addToWaitingList(userId);
                    return new PurchaseResult(false, "Added to waiting list", 0, pos);
                }
                if (stock.compareAndSet(current, current - 1)) {
                    return new PurchaseResult(true, "Success", current - 1, -1);
                }
            }
        }

        private int addToWaitingList(long userId) {
            waitingListLock.lock();
            try {
                Integer existing = waitingList.get(userId);
                if (existing != null) {
                    return existing;
                }
                int position = nextWaitingPosition.getAndIncrement();
                waitingList.put(userId, position);
                return position;
            } finally {
                waitingListLock.unlock();
            }
        }

        private List<Long> restockAndAllocate(int units) {
            List<Long> allocatedUsers = new ArrayList<>();
            if (units <= 0) {
                return allocatedUsers;
            }

            stock.addAndGet(units);

            waitingListLock.lock();
            try {
                Iterator<Map.Entry<Long, Integer>> iterator = waitingList.entrySet().iterator();
                while (stock.get() > 0 && iterator.hasNext()) {
                    Map.Entry<Long, Integer> entry = iterator.next();
                    if (stock.decrementAndGet() >= 0) {
                        allocatedUsers.add(entry.getKey());
                        iterator.remove();
                    } else {
                        stock.incrementAndGet();
                        break;
                    }
                }
            } finally {
                waitingListLock.unlock();
            }

            return allocatedUsers;
        }

        private int waitingListSize() {
            waitingListLock.lock();
            try {
                return waitingList.size();
            } finally {
                waitingListLock.unlock();
            }
        }
    }

    private final ConcurrentHashMap<String, ProductInventory> inventory;

    public FlashSaleInventoryManager(int expectedProducts) {
        int initialCapacity = Math.max(16, (int) (expectedProducts / 0.75f) + 1);
        this.inventory = new ConcurrentHashMap<>(
                initialCapacity,
                0.75f,
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
    }

    public void addProduct(String productId, int initialStock) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId cannot be null/empty");
        }
        if (initialStock < 0) {
            throw new IllegalArgumentException("initialStock cannot be negative");
        }
        inventory.put(productId, new ProductInventory(initialStock));
    }

    public int checkStock(String productId) {
        ProductInventory product = inventory.get(productId);
        if (product == null) {
            return -1;
        }
        return product.checkStock();
    }

    public PurchaseResult purchaseItem(String productId, long userId) {
        ProductInventory product = inventory.get(productId);
        if (product == null) {
            return new PurchaseResult(false, "Product not found", -1, -1);
        }
        return product.purchase(userId);
    }

    public List<Long> restockAndProcessWaitingList(String productId, int units) {
        ProductInventory product = inventory.get(productId);
        if (product == null) {
            return new ArrayList<>();
        }
        return product.restockAndAllocate(units);
    }

    public int waitingListSize(String productId) {
        ProductInventory product = inventory.get(productId);
        if (product == null) {
            return 0;
        }
        return product.waitingListSize();
    }

    public static void main(String[] args) throws InterruptedException {
        FlashSaleInventoryManager manager = new FlashSaleInventoryManager(32);
        String productId = "IPHONE15_256GB";
        manager.addProduct(productId, 100);

        System.out.println("checkStock(\"" + productId + "\") -> " + manager.checkStock(productId) + " units available");

        PurchaseResult r1 = manager.purchaseItem(productId, 12345L);
        System.out.println("purchaseItem(\"" + productId + "\", userId=12345) -> " + r1);

        PurchaseResult r2 = manager.purchaseItem(productId, 67890L);
        System.out.println("purchaseItem(\"" + productId + "\", userId=67890) -> " + r2);

        int customers = 50000;
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors() * 2));
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(customers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger waitListCount = new AtomicInteger(0);

        long startNs = System.nanoTime();

        for (int i = 1; i <= customers; i++) {
            final long userId = 1_000_000L + i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    PurchaseResult result = manager.purchaseItem(productId, userId);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else if ("Added to waiting list".equals(result.getMessage())) {
                        waitListCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        doneGate.await();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("=== Flash Sale Benchmark ===");
        System.out.println("Total requests: " + customers);
        System.out.println("Successful purchases: " + successCount.get());
        System.out.println("Waiting list additions: " + waitListCount.get());
        System.out.println("Remaining stock: " + manager.checkStock(productId));
        System.out.println("Waiting list size: " + manager.waitingListSize(productId));
        System.out.println("Elapsed time: " + elapsedMs + " ms");
        System.out.printf("Throughput: %.2f requests/sec%n", customers / Math.max(0.001, elapsedMs / 1000.0));

        PurchaseResult overflow = manager.purchaseItem(productId, 99999L);
        System.out.println("purchaseItem(\"" + productId + "\", userId=99999) -> " + overflow);
    }
}