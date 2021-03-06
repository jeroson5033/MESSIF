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
package messif.buckets.impl;

import messif.objects.LocalAbstractObject;
import java.io.Serializable;
import messif.buckets.BucketDispatcher;
import messif.buckets.BucketStorageException;
import messif.buckets.DuplicateObjectException;
import messif.buckets.LocalBucket;
import messif.buckets.OrderedLocalBucket;
import messif.buckets.index.LocalAbstractObjectOrder;
import messif.buckets.index.ModifiableOrderedIndex;
import messif.buckets.index.impl.IntStorageIndex;
import messif.buckets.storage.impl.MemoryStorage;


/**
 * A volatile implementation of {@link LocalBucket}.
 * It stores all objects in a {@link messif.buckets.storage.impl.MemoryStorage memory storage}.
 * Objects are indexed by their {@link LocalAbstractObject#dataHashCode() data} and
 * does not allow to store two objects with the same data. If an object that is
 * {@link messif.objects.LocalAbstractObject#dataEquals dataEquals} to
 * any object actually stored in the bucket, the <tt>addObject</tt> method will
 * throw {@link DuplicateObjectException}.
 * 
 * <p>
 * This bucket has an efficient {@link LocalBucket#deleteObject(messif.objects.LocalAbstractObject, int)} implementation
 * at the cost of additional memory overhead for maintaining the index.
 * If fast {@code deleteObject} implementation is not required and
 * the iteration over all objects is used frequently, consider using
 * {@link MemoryStorageBucket}.
 * </p>
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 * @see BucketDispatcher
 * @see LocalBucket
 */
public class MemoryStorageNoDupsBucket extends OrderedLocalBucket<LocalAbstractObject> implements Serializable {
    /** class serial id for serialization */
    private static final long serialVersionUID = 1L;

    //****************** Data storage ******************//

    /** Object storage with object-id index */
    protected ModifiableOrderedIndex<LocalAbstractObject, LocalAbstractObject> objects =
            new IntStorageIndex<LocalAbstractObject, LocalAbstractObject>(
                    new MemoryStorage<LocalAbstractObject>(LocalAbstractObject.class),
                    LocalAbstractObjectOrder.DATA
            ) {
                /** class serial id for serialization */
                private static final long serialVersionUID = 1L;

                @Override
                protected int insertionPoint(LocalAbstractObject object) throws BucketStorageException {
                    int pos = binarySearch(object, 0, size() - 1, true);
                    if (pos < 0)
                        return -pos - 1;
                    // Object was found in the index, throw duplicate exception
                    throw new DuplicateObjectException();
                }
            };


    /****************** Constructors ******************/

    /**
     * Constructs a new instance of MemoryStorageNoDupsBucket.
     * 
     * @param capacity maximal capacity of the bucket - cannot be exceeded
     * @param softCapacity maximal soft capacity of the bucket
     * @param lowOccupation a minimal occupation for deleting objects - cannot be lowered
     * @param occupationAsBytes flag whether the occupation (and thus all the limits) are in bytes or number of objects
     */
    public MemoryStorageNoDupsBucket(long capacity, long softCapacity, long lowOccupation, boolean occupationAsBytes) {
        super(capacity, softCapacity, lowOccupation, occupationAsBytes, 0);
    }


    //****************** Overrides ******************//

    @Override
    protected ModifiableOrderedIndex<LocalAbstractObject, LocalAbstractObject> getModifiableIndex() {
        return objects;
    }

    @Override
    public void finalize() throws Throwable {
        objects.finalize();
        super.finalize();
    }

    @Override
    public void destroy() throws Throwable {
        objects.destroy();
        super.destroy();
    }

}
