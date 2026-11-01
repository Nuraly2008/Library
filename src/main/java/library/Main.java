
package library;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int eventsPerProducer = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        Book book = new Book("B1", "Java Basics", 3, true);
        Journal journal = new Journal("J1", "Science Journal", 2, false);
        EBook ebook = new EBook("E1", "Clean Code", 0, true);

        Shelf firstShelf = new Shelf("Programming", List.of(book, ebook));
        Shelf secondShelf = new Shelf("Media", List.of(journal));

        Library library = new Library("City Library", List.of(
                new Section("Books", List.of(firstShelf)),
                new Section("Digital", List.of(secondShelf))
        ));

        AvailabilitySource availability = new AvailabilitySource();
        LoanSource loans = new LoanSource();

        ReservationNotifier reservations = new ReservationNotifier();
        LibrarianPanel panel = new LibrarianPanel();
        OverdueAlert overdue = new OverdueAlert();
        StatisticsAggregator statistics = new StatisticsAggregator();

        InspectionListener inspection = new InspectionListener(Map.of(
                "B1", firstShelf,
                "E1", firstShelf,
                "J1", secondShelf
        ));

        availability.subscribe(EventType.AVAILABLE, reservations);
        availability.subscribe(EventType.AVAILABLE, statistics);
        availability.subscribe(EventType.AVAILABLE, inspection);

        loans.subscribe(EventType.BORROWED, panel);
        loans.subscribe(EventType.BORROWED, statistics);
        loans.subscribe(EventType.OVERDUE, overdue);
        loans.subscribe(EventType.OVERDUE, statistics);

        statistics.subscribe(EventType.STATISTICS, panel);
        inspection.subscribe(EventType.INSPECTION, panel);

        LibrarySimulation simulation = new LibrarySimulation(
                availability,
                loans,
                List.of("B1", "J1", "E1")
        );

        simulation.run(threads, eventsPerProducer);

        InventoryVisitor inventory = new InventoryVisitor();
        library.accept(inventory);

        System.out.println("Total events: " + statistics.total());
        System.out.println("Reservations: " + reservations.notifications());
        System.out.println("Borrowed: " + panel.borrowed());
        System.out.println("Overdue alerts: " + overdue.alerts());
        System.out.println("Inspections: " + panel.inspections());
        System.out.println("Statistics updates: " + panel.statisticsUpdates());
        System.out.println("Inventory: " + inventory.total());
        System.out.println("Total fines: " + library.accept(new FineVisitor()));
        System.out.println("Overdue items: "+ library.accept(new OverdueFilterVisitor()).size());
        System.out.println("Catalog: " + library.accept(new JsonCatalogVisitor()));
    }
}

class LibrarySimulation {
    private final AvailabilitySource availability;
    private final LoanSource loans;
    private final List<String> itemIds;

    public LibrarySimulation(
            AvailabilitySource availability,
            LoanSource loans,
            List<String> itemIds) {

        this.availability = availability;
        this.loans = loans;
        this.itemIds = List.copyOf(itemIds);

        if (this.itemIds.isEmpty()) {
            throw new IllegalArgumentException("Item list cannot be empty");
        }
    }

    public void run(int threadCount, int eventsPerProducer)
            throws InterruptedException, ExecutionException {

        if (threadCount < 3 || eventsPerProducer < 0) {
            throw new IllegalArgumentException("Invalid simulation settings");
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int producer = 0; producer < threadCount; producer++) {
                final int producerId = producer;

                futures.add(executor.submit(() -> {
                    for (int i = 0; i < eventsPerProducer; i++) {
                        String id = itemIds.get((producerId + i) % itemIds.size());

                        switch ((producerId + i) % 3) {
                            case 0 -> availability.bookAvailable(id);
                            case 1 -> loans.bookBorrowed(id);
                            case 2 -> loans.bookOverdue(id);
                        }
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();

            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                throw new IllegalStateException("Executor did not terminate");
            }
        }
    }
}

