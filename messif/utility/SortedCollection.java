/*
 *  This file is part of MESSIF library.
 *
 *  MESSIF library is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  MESSIF library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with MESSIF library.  If not, see <http://www.gnu.org/licenses/>.
 */
package messif.utility;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * Implementation of a sorted collection.
 * The order is maintained using the comparator specified in the constructor.
 * Complexity of insertion is O(log n).
 *
 * @param <T> the type of objects stored in this collection
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class SortedCollection<T> extends SortedArrayData<T, T> implements Queue<T>, Serializable, Cloneable {
    /** class serial id for serialization */
    private static final long serialVersionUID = 1L;

    //****************** Constants ******************//

    /** Percentage of the capacity that is added when internal array is resized */
    private static final float SIZE_INCREASE_FACTOR = 0.3f;

    /** Default initial capacity of the internal array */
    protected static final int DEFAULT_INITIAL_CAPACITY = 16;

    /** Default initial capacity of the internal array */
    protected static final int UNLIMITED_CAPACITY = Integer.MAX_VALUE;


    //****************** Attributes ******************//

    /** Comparator used to maintain order in this collection */
    private final Comparator<? super T> comparator;

    /** Array buffer into which the elements of the SortedCollection are stored */
    private Object[] items;

    /** Size of the SortedCollection (the number of elements it contains) */
    private int size;

    /** Maximal capacity of the collection */
    private int capacity;

    /** Modifications counter, used for detecting iterator concurrent modification */
    private transient int modCount;


    //****************** Constructor ******************//

    /**
     * Constructs an empty collection with the specified initial and maximal capacity.
     * @param initialCapacity the initial capacity of the collection (if zero, the {@link #DEFAULT_INITIAL_CAPACITY} is used)
     * @param maximalCapacity the maximal capacity of the collection
     * @param comparator the comparator that defines ordering
     * @throws IllegalArgumentException if the specified initial or maximal capacity is invalid
     */
    public SortedCollection(int initialCapacity, int maximalCapacity, Comparator<? super T> comparator) throws IllegalArgumentException {
        if (comparator == null)
            this.comparator = new Comparable2IndexComparator<T>();
        else
            this.comparator = comparator;
        if (initialCapacity == 0)
            initialCapacity = maximalCapacity > 0 && maximalCapacity < DEFAULT_INITIAL_CAPACITY ? maximalCapacity : DEFAULT_INITIAL_CAPACITY;
        if (initialCapacity < 1)
            throw new IllegalArgumentException("Illegal capacity: " + initialCapacity);
        if (maximalCapacity < initialCapacity)
            throw new IllegalArgumentException("Illegal maximal capacity: " + maximalCapacity);

        this.capacity = maximalCapacity;
        this.items = new Object[initialCapacity];
    }

    /**
     * Constructs an empty collection with the specified initial capacity.
     * The capacity of this collection is not limited.
     * @param initialCapacity the initial capacity of the collection
     * @param comparator the comparator that defines ordering
     * @throws IllegalArgumentException if the specified initial or maximal capacity is invalid
     */
    public SortedCollection(int initialCapacity, Comparator<? super T> comparator) throws IllegalArgumentException {
        this(initialCapacity, UNLIMITED_CAPACITY, comparator);
    }

    /**
     * Constructs an empty collection.
     * The initial capacity of the collection is set to 16 and maximal capacity
     * is not limited.
     * @param comparator the comparator that defines ordering
     */
    public SortedCollection(Comparator<? super T> comparator) {
        this(DEFAULT_INITIAL_CAPACITY, comparator);
    }

    /**
     * Constructs an empty collection with the specified initial capacity.
     * The order is defined using the natural order of items.
     * The capacity of this collection is not limited.
     * @param initialCapacity the initial capacity of the collection
     * @throws IllegalArgumentException if the specified initial or maximal capacity is invalid
     */
    public SortedCollection(int initialCapacity) throws IllegalArgumentException {
        this(initialCapacity, null);
    }

    /**
     * Constructs an empty collection.
     * The order is defined using the natural order of items.
     * The initial capacity of the collection is set to {@link #DEFAULT_INITIAL_CAPACITY}
     * and maximal capacity is not limited.
     */
    public SortedCollection() {
        this(null);
    }


    //****************** Comparing methods ******************//

    /**
     * Internal comparator that compares {@link Comparable} objects.
     * @param <T> type of objects to compare
     */
    private static final class Comparable2IndexComparator<T> implements Comparator<T>, Serializable {
        /** class serial id for serialization */
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("unchecked")
        @Override
        public int compare(T o1, T o2) {
            return ((Comparable)o1).compareTo(o2); // This is unchecked but a responsibility of the constructor caller
        }
    }

    @Override
    protected int compare(T key, T object) throws ClassCastException {
        return comparator.compare(key, object);
    }

    /**
     * Returns the comparator used by this collection.
     * @return the comparator used by this collection
     */
    public Comparator<? super T> getComparator() {
        return comparator;
    }


    //****************** Data access methods ******************//

    /**
     * Returns the number of elements in this collection.
     * @return the number of elements in this collection
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Returns <tt>true</tt> if this collection contains no elements.
     * @return <tt>true</tt> if this collection contains no elements
     */
    @Override
    public boolean isEmpty() {
	return size == 0;
    }

    /**
     * Returns <tt>true</tt> if this collection contains the maximal number of elements.
     * @return <tt>true</tt> if this collection contains the maximal number of elements
     */
    public boolean isFull() {
	return size == capacity;
    }

    /**
     * Returns the maximal capacity of this collection.
     * @return the maximal capacity of this collection
     */
    public int getMaximalCapacity() {
        return capacity;
    }

    /**
     * Set the maximal capacity of this collection. The new capacity cannot be smaller then its {@link #size}.
     * @param capacity the new collection maximal capacity (zero or negative value means unlimited capacity)
     */
    protected void setMaximalCapacity(int capacity) {
        if (capacity < size)
            throw new IllegalStateException("Maximal collection capacity cannot be smaller than collection actual size");
        if (capacity <= 0) {
            this.capacity = UNLIMITED_CAPACITY;
        } else {
            this.capacity = capacity;
            if (items.length > capacity) {
                Object[] newItems = new Object[capacity];
                System.arraycopy(items, 0, newItems, 0, size);
                items = newItems;
            }
        }
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this list contains
     * at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return <tt>true</tt> if this list contains the specified element
     */
    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * Returns <tt>true</tt> if this collection contains all of the elements
     * in the specified collection.
     *
     * @param  c collection to be checked for containment in this collection
     * @return <tt>true</tt> if this collection contains all of the elements
     *	       in the specified collection
     * @throws NullPointerException if the specified collection is null
     * @see    #contains(Object)
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object item : c)
            if (indexOf(item) < 0)
                return false;
        return true;
    }

    /**
     * Returns the element at the specified position in this collection.
     * @param  index index of the element to return
     * @return the element at the specified position in this collection
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    @SuppressWarnings("unchecked")
    @Override
    protected final T get(int index) {
        return (T)items[index];
    }


    /**
     * Removes and returns the last element of this collection according to the order
     * specified by the comparator.
     * @return the last element in this collection
     * @throws NoSuchElementException if the collection is empty
     */
    public T removeLast() throws NoSuchElementException {
        if (isEmpty())
            throw new NoSuchElementException();
        T last = get(size - 1);
        remove(size - 1);
        return last;
    }

    /**
     * Removes and returns the last element of this collection according to the order
     * specified by the comparator.
     * @return the last element in this collection
     * @throws NoSuchElementException if the collection is empty
     * @deprecated Use {@link #removeLast() removeLast()} method instead
     */
    @Deprecated
    public T popLast() throws NoSuchElementException {
        return removeLast();
    }

    /**
     * Removes and returns the first element of this collection according to the order
     * specified by the comparator.
     * @return the first element in this collection
     * @throws NoSuchElementException if the collection is empty
     */
    public T removeFirst() throws NoSuchElementException {
        if (isEmpty())
            throw new NoSuchElementException();
        T first = get(0);
        remove(0);
        return first;
    }

    /**
     * Removes the specified number of top elements of this collection according to the order
     * specified by the comparator.
     * @param count number of objects to be removed
     * @throws NoSuchElementException if less then specified number of objects is stored
     */
    public void removeFirstN(int count) throws NoSuchElementException {
        if (size < count)
            throw new NoSuchElementException();
        removeAll(0, count);
    }
    

    // *********************     Queue   implementation   ***************** //
    
    @Override
    public boolean offer(T e) {
        return add(e);
    }

    @Override
    public T poll() {
        try {
            return removeFirst();
        } catch (NoSuchElementException ignore) {
            return null;
        }
    }

    @Override
    public T remove() {
        return removeFirst();
    }
    
    @Override
    public T peek() {
        try {
            return first();
        } catch (NoSuchElementException e) {
            return null;
        }
    }
    
    @Override
    public T element() {
        return first();
    }

    
    //****************** Copy methods ******************//

    /**
     * Returns a shallow copy of this <tt>SortedCollection</tt> instance.
     * The elements themselves are copied as references only and the comparator is shared.
     * @return a clone of this <tt>SortedCollection</tt> instance
     * @throws CloneNotSupportedException if there was a problem cloning this collection
     */
    @Override
    public final SortedCollection<T> clone() throws CloneNotSupportedException {
        return clone(true);
    }

    /**
     * Returns a shell or shallow copy of this <tt>SortedCollection</tt> instance.
     * If {@code copyData} parameter is <tt>false</tt>, only the collection shell
     * is copied but no data, i.e. this creates a new instance of an empty collection
     * with the same settings as the original one. Otherwise, the data are also copied
     * as references.
     * The comparator is shared with the new instance.
     * @param copyData if <tt>true</tt> the collection data are copied as references, otherwise,
     *      only the collection shell is copied but no data, i.e. this creates a new instance of
     *      an empty collection with the same settings as the original one
     * @return a clone of this <tt>SortedCollection</tt> instance
     * @throws CloneNotSupportedException if there was a problem cloning this collection
     */
    public SortedCollection<T> clone(boolean copyData) throws CloneNotSupportedException {
        @SuppressWarnings("unchecked")
        SortedCollection<T> ret = (SortedCollection<T>)super.clone(); // This uncheck IS correct, since this is cloning
        if (copyData) {
            ret.items = new Object[size];
            System.arraycopy(items, 0, ret.items, 0, size);
        } else {
            ret.items = new Object[DEFAULT_INITIAL_CAPACITY];
            ret.size = 0;
        }
        ret.modCount = 0;
        return ret;

    }

    /**
     * Returns an array containing all of the elements in this list
     * in proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this list.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this list in
     *         proper sequence
     */
    @Override
    public Object[] toArray() {
        Object[] ret = new Object[size];
        System.arraycopy(items, 0, ret, 0, size);
        return ret;
    }

    /**
     * Returns an array containing all of the elements in this list in proper
     * sequence (from first to last element); the runtime type of the returned
     * array is that of the specified array.  If the list fits in the
     * specified array, it is returned therein.  Otherwise, a new array is
     * allocated with the runtime type of the specified array and the size of
     * this list.
     *
     * <p>If the list fits in the specified array with room to spare
     * (i.e., the array has more elements than the list), the element in
     * the array immediately following the end of the collection is set to
     * <tt>null</tt>.  (This is useful in determining the length of the
     * list <i>only</i> if the caller knows that the list does not contain
     * any null elements.)
     *
     * @param <E> the type of array components
     * @param array the array into which the elements of the list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the list
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this list
     * @throws NullPointerException if the specified array is null
     */
    @Override
    public <E> E[] toArray(E[] array) {
        if (array.length < size)
            array = Convert.createGenericArray(array, size);
	System.arraycopy(items, 0, array, 0, size);
        if (array.length > size)
            array[size] = null;
        return array;
    }


    //****************** Modification methods ******************//

    /**
     * Adds the specified element to this list.
     * The element is added according the to order defined by the comparator.
     * @param e element to be appended to this list
     * @return <tt>true</tt> if the object was added to the collection or
     *          <tt>false</tt> if not (e.g. because of the limited capacity of the collection)
     */
    @Override
    public boolean add(T e) {
        return add(e, binarySearch(e, 0, size - 1, false));
    }
    
    /**
     * Adds the specified element to this list given an index on which the object should be stored.
     * Use carefully - this method does not check, if the order of the objects is preserved.
     * @param e element to be appended to this list
     * @param index index on which the object should be stored
     * @return <tt>true</tt> if the object was added to the collection or
     *          <tt>false</tt> if not (e.g. because of the limited capacity of the collection)
     */
    protected boolean add(T e, int index) {
        modCount++;

        // If array is too small to hold new item
        if (size == items.length) {
            // If the capacity limit is not reached yet
            if (items.length < capacity) {
                // Compute new capacity
                int newSize = items.length + 1 + (int)(items.length * SIZE_INCREASE_FACTOR);
                if (newSize > capacity)
                    newSize = capacity;

                // Create new array
                Object[] newItems = new Object[newSize];
                System.arraycopy(items, 0, newItems, 0, index);

                // Add object
                if (index < size)
                    System.arraycopy(items, index, newItems, index + 1, size - index);
                newItems[index] = e;
                items = newItems;
                size++;
            } else { // No capacity left for storing object
                // If insertion point is after last object
                if (index >= size)
                    return false;

                // Insert in the middle (effectively destroying the last object)
                System.arraycopy(items, index, items, index + 1, size - index - 1);
                items[index] = e;
            }
        } else {
            // There is enough space
            if (index < size)
                System.arraycopy(items, index, items, index + 1, size - index);
            items[index] = e;
            size++;
        }

        return true;
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If the list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns <tt>true</tt> if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return <tt>true</tt> if this list contained the specified element
     */
    @Override
    public boolean remove(Object o) {
        int index = indexOf(o);
        return (index < 0)?false:remove(index);
    }

    /**
     * Removes the element at the specified position in this collection.
     * @param index index of the element to remove
     * @return <tt>false</tt> if the object was not removed (e.g. because there is no object with this index)
     */
    protected boolean remove(int index) {
        if (index < 0 || index >= size)
            return false;
        modCount++;
        if (index < size - 1)
            System.arraycopy(items, index + 1, items, index, size - index - 1);
        items[--size] = null; // Let gc do its work
        return true;
    }

    /**
     * Removes from this collection the specified number of elements starting at the specified position. If less than 
     *  specified number of objects is stored, then the collection is cleared.
     * @param indexFrom index of the first element to removed (inclusive)
     * @param number number of objects to be removed from this collection
     * @return <tt>false</tt> if the objects were not removed (e.g. because there is no object with this index)
     */
    protected boolean removeAll(int indexFrom, int number) {
        if (indexFrom < 0 || indexFrom >= size || number <= 0)
            return false;
        modCount++;
        int indexTo = indexFrom + number; //final index (exclusive)
        if (indexTo < size)
            System.arraycopy(items, indexTo, items, indexFrom, size - indexTo);
        for (int i = size - number; i < size; i++) {
            items[i] = null; // Let gc do its work
        }
        size -= number;
        return true;
    }
    
    //****************** Bulk modification methods ******************//

    /**
     * Add all of the elements in the specified collection to this list.
     * The elements are added according the to order defined by the comparator. If the specified
     *  collection is of type {@link SortedCollection} over the same type, then it is assumed that
     *  it is ordered by the same comparator and these lists are effectively merged.
     * @param c collection containing elements to be added to this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     */
    @Override
    public boolean addAll(Collection<? extends T> c) {
	boolean ret = false;
        for (T t : c) {
            if (add(t))
                ret = true;            
        }
        return ret;
    }

    /**
     * Add all of the elements in the specified array to this list assuming that the
     *  items of the {@code array} are ordered according to the same {@link #comparator} as
     *  this collection. If capacity C would be exceeded, then only the top-C objects remain in the merged answer.
     * @param array array of objects ordered according to the same {@link #comparator} as this collection
     * @param size number of elements from the array to be added to this collection (the rest is ignored)
     * @return <tt>true</tt> if this list changed as a result of the call; false if, e.g., the resulting collection
     *  should exceed the capacity (in this case no data from {@code array} is added.
     * @throws NullPointerException if the specified array is null
     */
    protected boolean addAllSortedArray(Object[] array, int size) {
        Object [] newArray = new Object[Math.min(size + this.size, capacity)];
        mergeSort((T[]) this.items, 0, this.size, (T[]) array, 0, size, comparator, (T[]) newArray, 0, newArray.length);
        this.items = newArray;
        this.size = newArray.length;
        return true;
    }
    
    /**
     * Removes all of this collection's elements that are also contained in the
     * specified collection. After this call returns, this collection will contain
     * no elements in common with the specified collection.
     *
     * @param c collection containing elements to be removed from this collection
     * @return <tt>true</tt> if this collection changed as a result of the
     *         call
     * @throws NullPointerException if the specified collection is null
     * @see #remove(Object)
     * @see #contains(Object)
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        boolean ret = false;
        Iterator<T> iterator = iterator();
        while (iterator.hasNext()) {
            if (c.contains(iterator.next())) {
                ret = true;
                iterator.remove();
            }
        }

        return ret;
    }

    /**
     * Retains only the elements in this collection that are contained in the
     * specified collection. In other words, removes from this collection all
     * of its elements that are not contained in the specified collection.
     *
     * @param c collection containing elements to be retained in this collection
     * @return <tt>true</tt> if this collection changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     * @see #remove(Object)
     * @see #contains(Object)
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        boolean ret = false;
        Iterator<T> iterator = iterator();
        while (iterator.hasNext()) {
            if (!c.contains(iterator.next())) {
                ret = true;
                iterator.remove();
            }
        }

        return ret;
    }

    /**
     * Removes all of the elements from this list.  The list will
     * be empty after this call returns.
     */
    @Override
    public void clear() {
	modCount++;

	// Let gc do its work
	for (int i = 0; i < size; i++)
	    items[i] = null;

	size = 0;
    }


    //****************** String conversion ******************//

    /**
     * Returns a string representation of this collection. The string
     * representation consists of a list of the collection's elements in the
     * order defined by the comparator, enclosed in square brackets
     * (<tt>"[]"</tt>).  Adjacent elements are separated by the characters
     * <tt>", "</tt> (comma and space).  Elements are converted to strings as
     * by {@link String#valueOf(Object)}.
     *
     * @return a string representation of this collection
     */
    @Override
    public String toString() {
        if (isEmpty())
            return "[]";

	StringBuilder sb = new StringBuilder();
        sb.append('[');
        sb.append(items[0]); // This is correct since collection is not empty
	for (int i = 1; i < size; i++)
            sb.append(", ").append(items[i]);
        return sb.append(']').toString();
    }


    //****************** Iterator ******************//

    /**
     * Returns an iterator over the elements in this collection. Their order
     * is defined by the comparator.
     *
     * @return an iterator over the elements in this collection
     */
    @Override
    public Iterator<T> iterator() {
        return new Itr(0, size);
    }

    /**
     * Returns an iterator over the elements in this collection skipping the first
     * {@code skip} items and returning only {@code count} elements. If {@code count}
     * is less than or equal to zero, all objects from the collection (except for
     * {@code skip}) are returned. Note that their order is defined by the comparator.
     *
     * @param skip number of items to skip
     * @param count number of items to iterate (maximally, can be less)
     * @return an iterator over the elements in this collection
     */
    public Iterator<T> iterator(int skip, int count) {
        if (skip < 0)
            throw new IllegalArgumentException("Skip argument cannot be negative");
        int iteratorSize;
        if (count <= 0)
            iteratorSize = size;
        else if (skip + count > size)
            iteratorSize = size;
        else
            iteratorSize = count + skip;
        return new Itr(skip, iteratorSize);
    }

    /** Internal class that implements iterator for this collection */
    private class Itr implements Iterator<T> {
	/** Index of an element to be returned by subsequent call to next */
	private int cursor;

	/** Index of the last element to return plus one */
	private int iteratorSize;

	/**
	 * Index of element returned by most recent call to next or
	 * previous.  Reset to -1 if this element is deleted by a call
	 * to remove.
	 */
	private int lastRet = -1;

	/**
	 * The modCount value that the iterator believes that the backing
	 * List should have.  If this expectation is violated, the iterator
	 * has detected concurrent modification.
	 */
	private int expectedModCount = modCount;

        /**
         * Creates a new iterator instance.
         * @param cursor the index of initial item where the iteration starts
         * @param iteratorSize index of the last element to return plus one
         */
        private Itr(int cursor, int iteratorSize) {
            this.cursor = cursor;
            this.iteratorSize = iteratorSize;
        }

        @Override
	public boolean hasNext() {
            return cursor < iteratorSize;
	}

        @Override
	public T next() {
            checkForComodification();
	    if (!hasNext())
                throw new NoSuchElementException();
            T next = get(cursor);
            lastRet = cursor++;
            return next;
	}

        @Override
	public void remove() {
	    if (lastRet == -1)
		throw new IllegalStateException();
            checkForComodification();

	    try {
		SortedCollection.this.remove(lastRet);
		if (lastRet < cursor)
		    cursor--;
		lastRet = -1;
		expectedModCount = modCount;
                iteratorSize--;
	    } catch (IndexOutOfBoundsException e) {
		throw new ConcurrentModificationException();
	    }
	}

        /**
         * Internal method that checks for the modification of this collection
         * during iteration and throws ConcurrentModificationException.
         * @throws ConcurrentModificationException if the collection was modified while iterating
         */
        private void checkForComodification() throws ConcurrentModificationException {
	    if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

}
