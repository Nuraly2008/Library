
package library;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LibraryTest {

    private Library sampleLibrary() {
        Book book = new Book("B1", "Java", 3, true);
        Journal journal = new Journal("J1", "Science", 2, false);
        EBook ebook = new EBook("E1", "Clean Code", 0, true);

        Shelf first = new Shelf("Programming", List.of(book, ebook));
        Shelf second = new Shelf("Media", List.of(journal));

        return new Library("City Library", List.of(
                new Section("Books", List.of(first)),
                new Section("Digital", List.of(second))
        ));
    }

    @Test
    void calculatesTotalFines() {
        assertEquals(13, sampleLibrary().accept(new FineVisitor()));
    }

    @Test
    void calculatesFinesForOneShelf() {
        Shelf shelf = sampleLibrary().sections().get(0).shelves().get(0);
        assertEquals(9, shelf.accept(new FineVisitor()));
    }

    @Test
    void countsInventory() {
        InventoryVisitor visitor = new InventoryVisitor();
        sampleLibrary().accept(visitor);

        assertEquals(3, visitor.total());
        assertEquals(1, visitor.count("Book"));
        assertEquals(1, visitor.count("Journal"));
        assertEquals(1, visitor.count("EBook"));
    }

    @Test
    void accumulatesInventoryAcrossSections() {
        InventoryVisitor visitor = new InventoryVisitor();
        Library library = sampleLibrary();

        library.sections().get(0).accept(visitor);
        library.sections().get(1).accept(visitor);

        assertEquals(3, visitor.total());
    }

    @Test
    void exportsNestedJson() {
        Library library = new Library("L", List.of(
                new Section("S", List.of(
                        new Shelf("H", List.of(
                                new Book("B", "T", 0, true)
                        ))
                ))
        ));

        String expected = "{\"library\":\"L\",\"sections\":["
                + "{\"section\":\"S\",\"shelves\":["
                + "{\"shelf\":\"H\",\"items\":["
                + "{\"type\":\"book\",\"id\":\"B\",\"title\":\"T\","
                + "\"overdueDays\":0,\"available\":true}"
                + "]}]}]}";

        assertEquals(expected, library.accept(new JsonCatalogVisitor()));
    }

    @Test
    void escapesJsonCharacters() {
        Book book = new Book("B\"1", "Line\nTwo", 0, true);
        String json = book.accept(new JsonCatalogVisitor());

        assertTrue(json.contains("\"id\":\"B\\\"1\""));
        assertTrue(json.contains("\"title\":\"Line\\nTwo\""));
    }

    @Test
    void subscribesByEventType() {
        LoanSource source = new LoanSource();
        AtomicInteger count = new AtomicInteger();

        source.subscribe(EventType.OVERDUE, event -> count.incrementAndGet());

        source.bookBorrowed("B1");
        source.bookOverdue("B1");

        assertEquals(1, count.get());
    }

    @Test
    void preventsDuplicateSubscriptions() {
        AvailabilitySource source = new AvailabilitySource();
        AtomicInteger count = new AtomicInteger();
        Observer<LibraryEvent> observer = event -> count.incrementAndGet();

        source.subscribe(EventType.AVAILABLE, observer);
        source.subscribe(EventType.AVAILABLE, observer);
        source.bookAvailable("B1");

        assertEquals(1, count.get());
    }

    @Test
    void supportsUnsubscribe() {
        AvailabilitySource source = new AvailabilitySource();
        AtomicInteger count = new AtomicInteger();
        Observer<LibraryEvent> observer = event -> count.incrementAndGet();

        source.subscribe(EventType.AVAILABLE, observer);
        source.bookAvailable("B1");

        source.unsubscribe(EventType.AVAILABLE, observer);
        source.bookAvailable("B1");

        assertEquals(1, count.get());
    }

    @Test
    void supportsUnsubscribeInsideHandler() {
        AvailabilitySource source = new AvailabilitySource();
        AtomicInteger count = new AtomicInteger();
        AtomicReference<Observer<LibraryEvent>> reference = new AtomicReference<>();

        reference.set(event -> {
            count.incrementAndGet();
            source.unsubscribe(EventType.AVAILABLE, reference.get());
        });

        source.subscribe(EventType.AVAILABLE, reference.get());

        source.bookAvailable("B1");
        source.bookAvailable("B1");

        assertEquals(1, count.get());
    }

    @Test
    void supportsSubscribeInsideHandler() {
        AvailabilitySource source = new AvailabilitySource();
        AtomicInteger count = new AtomicInteger();
        Observer<LibraryEvent> second = event -> count.incrementAndGet();

        source.subscribe(EventType.AVAILABLE, event ->
                source.subscribe(EventType.AVAILABLE, second));

        source.bookAvailable("B1");
        assertEquals(0, count.get());

        source.bookAvailable("B1");
        assertEquals(1, count.get());
    }

    @Test
    void keepsSourcesIndependent() {
        AvailabilitySource availability = new AvailabilitySource();
        LoanSource loans = new LoanSource();
        AtomicInteger count = new AtomicInteger();

        availability.subscribe(EventType.AVAILABLE,
                event -> count.incrementAndGet());

        loans.bookBorrowed("B1");
        assertEquals(0, count.get());

        availability.bookAvailable("B1");
        assertEquals(1, count.get());
    }

    @Test
    void aggregatorPublishesEvents() {
        AvailabilitySource source = new AvailabilitySource();
        StatisticsAggregator aggregator = new StatisticsAggregator();
        LibrarianPanel panel = new LibrarianPanel();

        source.subscribe(EventType.AVAILABLE, aggregator);
        aggregator.subscribe(EventType.STATISTICS, panel);

        source.bookAvailable("B1");
        source.bookAvailable("B2");

        assertEquals(2, aggregator.total());
        assertEquals(2, panel.statisticsUpdates());
        assertEquals(2, panel.lastTotal());
    }

    @Test
    void inspectionUsesOnlyRelatedShelf() {
        Shelf first = new Shelf("First", List.of(
                new Book("B1", "Java", 3, true)
        ));

        Shelf second = new Shelf("Second", List.of(
                new Journal("J1", "Science", 20, false)
        ));

        InspectionListener inspection = new InspectionListener(Map.of(
                "B1", first,
                "J1", second
        ));

        LibrarianPanel panel = new LibrarianPanel();
        inspection.subscribe(EventType.INSPECTION, panel);

        inspection.update(new LibraryEvent(EventType.AVAILABLE, "B1", 1));

        assertEquals(1, panel.inspections());
        assertEquals(9, panel.inspectionFine());
    }

    @Test
    void handlesFourThousandEventsExactly() {
        AvailabilitySource availability = new AvailabilitySource();
        LoanSource loans = new LoanSource();
        StatisticsAggregator aggregator = new StatisticsAggregator();
        LibrarianPanel panel = new LibrarianPanel();

        availability.subscribe(EventType.AVAILABLE, aggregator);
        loans.subscribe(EventType.BORROWED, aggregator);
        loans.subscribe(EventType.OVERDUE, aggregator);
        aggregator.subscribe(EventType.STATISTICS, panel);

        LibrarySimulation simulation = new LibrarySimulation(
                availability,
                loans,
                List.of("B1", "J1", "E1")
        );

        assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> simulation.run(4, 1000));

        assertEquals(4000, aggregator.total());
        assertEquals(4000, panel.statisticsUpdates());
        assertEquals(4000, panel.lastTotal());
    }
}
