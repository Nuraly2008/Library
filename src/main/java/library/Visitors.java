
package library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

class FineVisitor implements Visitor<Integer> {
    private int total(List<? extends LibraryElement> elements) {
        int sum = 0;

        for (LibraryElement element : elements) {
            sum += element.accept(this);
        }

        return sum;
    }

    public Integer visit(Library library) {
        return total(library.sections());
    }

    public Integer visit(Section section) {
        return total(section.shelves());
    }

    public Integer visit(Shelf shelf) {
        return total(shelf.items());
    }

    public Integer visit(Book book) {
        return book.overdueDays() * 3;
    }

    public Integer visit(Journal journal) {
        return journal.overdueDays() * 2;
    }

    public Integer visit(EBook ebook) {
        return ebook.overdueDays();
    }
}

class InventoryVisitor implements Visitor<Void> {
    private final Map<String, Integer> counts = new HashMap<>();

    private void collect(List<? extends LibraryElement> elements) {
        for (LibraryElement element : elements) {
            element.accept(this);
        }
    }

    private void add(String type) {
        counts.merge(type, 1, Integer::sum);
    }

    public int count(String type) {
        return counts.getOrDefault(type, 0);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Void visit(Library library) {
        collect(library.sections());
        return null;
    }

    public Void visit(Section section) {
        collect(section.shelves());
        return null;
    }

    public Void visit(Shelf shelf) {
        collect(shelf.items());
        return null;
    }

    public Void visit(Book book) {
        add("Book");
        return null;
    }

    public Void visit(Journal journal) {
        add("Journal");
        return null;
    }

    public Void visit(EBook ebook) {
        add("EBook");
        return null;
    }
}

class JsonCatalogVisitor implements Visitor<String> {
    private String array(List<? extends LibraryElement> elements) {
        StringJoiner result = new StringJoiner(",", "[", "]");

        for (LibraryElement element : elements) {
            result.add(element.accept(this));
        }

        return result.toString();
    }

    private String quote(String value) {
        StringBuilder result = new StringBuilder("\"");

        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (c < 32) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }

        return result.append('"').toString();
    }

    private String item(String type, CatalogItem item) {
        return "{\"type\":" + quote(type)
                + ",\"id\":" + quote(item.id())
                + ",\"title\":" + quote(item.title())
                + ",\"overdueDays\":" + item.overdueDays()
                + ",\"available\":" + item.available() + "}";
    }

    public String visit(Library library) {
        return "{\"library\":" + quote(library.name())
                + ",\"sections\":" + array(library.sections()) + "}";
    }

    public String visit(Section section) {
        return "{\"section\":" + quote(section.name())
                + ",\"shelves\":" + array(section.shelves()) + "}";
    }

    public String visit(Shelf shelf) {
        return "{\"shelf\":" + quote(shelf.name())
                + ",\"items\":" + array(shelf.items()) + "}";
    }

    public String visit(Book book) {
        return item("book", book);
    }

    public String visit(Journal journal) {
        return item("journal", journal);
    }

    public String visit(EBook ebook) {
        return item("ebook", ebook);
    }
}

class OverdueFilterVisitor implements Visitor<List<CatalogItem>> {
    private List<CatalogItem> collect(List<? extends LibraryElement> elements) {
        List<CatalogItem> result = new ArrayList<>();

        for (LibraryElement element : elements) {
            result.addAll(element.accept(this));
        }

        return result;
    }

    private List<CatalogItem> check(CatalogItem item) {
        return item.overdueDays() > 0 ? List.of(item) : List.of();
    }

    public List<CatalogItem> visit(Library library) {
        return collect(library.sections());
    }

    public List<CatalogItem> visit(Section section) {
        return collect(section.shelves());
    }

    public List<CatalogItem> visit(Shelf shelf) {
        return collect(shelf.items());
    }

    public List<CatalogItem> visit(Book book) {
        return check(book);
    }

    public List<CatalogItem> visit(Journal journal) {
        return check(journal);
    }

    public List<CatalogItem> visit(EBook ebook) {
        return check(ebook);
    }
}

