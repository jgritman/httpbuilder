package groovyx.net.http;

import java.util.function.Supplier;
import java.util.function.Predicate;

public interface Traversible<T> {

    Traversible<T> getParent();

    default public <V> V traverse(final Supplier<V> supplier, final Predicate<V> predicate) {
        if(predicate.test(supplier.get())) {
            return supplier.get();
        }

        if(getParent() != null) {
            return getParent().traverse(supplier, predicate);
        }

        return null;
    }

    public static <V> boolean notNull(final V v) { return v != null; }
    public static <V> Predicate<V> notValue(final V v) { return (toTest) -> !v.equals(toTest); }
}
