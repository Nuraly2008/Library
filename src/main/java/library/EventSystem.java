
package library;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

enum EventType {
    AVAILABLE, BORROWED, OVERDUE, STATISTICS, INSPECTION
}

interface TypedEvent {
    EventType type();
}

record LibraryEvent(EventType type, String itemId, int value) implements TypedEvent {
}

interface Observer<E extends TypedEvent> {
    void update(E event);
}

interface Subject<E extends TypedEvent> {
    void subscribe(EventType type, Observer<E> observer);
    void unsubscribe(EventType type, Observer<E> observer);
}

class EventSubject<E extends TypedEvent> implements Subject<E> {
    private final Map<EventType, CopyOnWriteArrayList<Observer<E>>> observers =
            new ConcurrentHashMap<>();

    public void subscribe(EventType type, Observer<E> observer) {
        observers.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .addIfAbsent(observer);
    }

    public void unsubscribe(EventType type, Observer<E> observer) {
        var list = observers.get(type);
        if (list != null) {
            list.remove(observer);
        }
    }

    public void publish(E event) {
        var list = observers.get(event.type());
        if (list != null) {
            for (Observer<E> observer : list) {
                observer.update(event);
            }
        }
    }
}

class AvailabilitySource extends EventSubject<LibraryEvent> {
    public void bookAvailable(String id) {
        publish(new LibraryEvent(EventType.AVAILABLE, id, 1));
    }
}

class LoanSource extends EventSubject<LibraryEvent> {
    public void bookBorrowed(String id) {
        publish(new LibraryEvent(EventType.BORROWED, id, 1));
    }

    public void bookOverdue(String id) {
        publish(new LibraryEvent(EventType.OVERDUE, id, 1));
    }
}

class ReservationNotifier implements Observer<LibraryEvent> {
    private final AtomicInteger notifications = new AtomicInteger();

    public void update(LibraryEvent event) {
        notifications.incrementAndGet();
    }

    public int notifications() {
        return notifications.get();
    }
}

class LibrarianPanel implements Observer<LibraryEvent> {
    private final AtomicInteger borrowed = new AtomicInteger();
    private final AtomicInteger inspections = new AtomicInteger();
    private final AtomicInteger inspectionFine = new AtomicInteger();
    private final AtomicInteger statisticsUpdates = new AtomicInteger();
    private final AtomicInteger lastTotal = new AtomicInteger();

    public void update(LibraryEvent event) {
        switch (event.type()) {
            case BORROWED -> borrowed.incrementAndGet();
            case INSPECTION -> {
                inspections.incrementAndGet();
                inspectionFine.set(event.value());
            }
            case STATISTICS -> {
                statisticsUpdates.incrementAndGet();
                lastTotal.accumulateAndGet(event.value(), Math::max);
            }
            default -> {
            }
        }
    }

    public int borrowed() {
        return borrowed.get();
    }

    public int inspections() {
        return inspections.get();
    }

    public int inspectionFine() {
        return inspectionFine.get();
    }

    public int statisticsUpdates() {
        return statisticsUpdates.get();
    }

    public int lastTotal() {
        return lastTotal.get();
    }
}

class OverdueAlert implements Observer<LibraryEvent> {
    private final AtomicInteger alerts = new AtomicInteger();

    public void update(LibraryEvent event) {
        alerts.incrementAndGet();
    }

    public int alerts() {
        return alerts.get();
    }
}

class StatisticsAggregator extends EventSubject<LibraryEvent>
        implements Observer<LibraryEvent> {

    private final AtomicInteger total = new AtomicInteger();

    public void update(LibraryEvent event) {
        int count = total.incrementAndGet();
        publish(new LibraryEvent(EventType.STATISTICS, "library", count));
    }

    public int total() {
        return total.get();
    }
}

class InspectionListener extends EventSubject<LibraryEvent>
        implements Observer<LibraryEvent> {

    private final Map<String, Shelf> shelvesByItem;

    public InspectionListener(Map<String, Shelf> shelvesByItem) {
        this.shelvesByItem = Map.copyOf(shelvesByItem);
    }

    public void update(LibraryEvent event) {
        Shelf shelf = shelvesByItem.get(event.itemId());

        if (shelf != null) {
            int fine = shelf.accept(new FineVisitor());
            publish(new LibraryEvent(EventType.INSPECTION, shelf.name(), fine));
        }
    }
}
