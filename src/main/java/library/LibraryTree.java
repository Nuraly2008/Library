
package library;

import java.util.List;

interface LibraryElement {
    <R> R accept(Visitor<R> visitor);
}

interface CatalogItem extends LibraryElement {
    String id();
    String title();
    int overdueDays();
    boolean available();
}

interface Visitor<R> {
    R visit(Library library);
    R visit(Section section);
    R visit(Shelf shelf);
    R visit(Book book);
    R visit(Journal journal);
    R visit(EBook ebook);
    R visit(AudioBook audiobook);
}

record Library(String name, List<Section> sections) implements LibraryElement {
    Library {
        sections = List.copyOf(sections);
    }

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

record Section(String name, List<Shelf> shelves) implements LibraryElement {
    Section {
        shelves = List.copyOf(shelves);
    }

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

record Shelf(String name, List<CatalogItem> items) implements LibraryElement {
    Shelf {
        items = List.copyOf(items);
    }

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

record Book(String id, String title, int overdueDays, boolean available)
        implements CatalogItem {

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

record Journal(String id, String title, int overdueDays, boolean available)
        implements CatalogItem {

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

record EBook(String id, String title, int overdueDays, boolean available)
        implements CatalogItem {

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

record AudioBook(String id, String title, int overdueDays, boolean available)
        implements CatalogItem {

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

