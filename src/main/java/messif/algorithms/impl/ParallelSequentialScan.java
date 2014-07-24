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
package messif.algorithms.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import messif.algorithms.Algorithm;
import messif.algorithms.AsynchronousNavigationProcessor;
import messif.algorithms.NavigationDirectory;
import messif.buckets.BucketDispatcher;
import messif.buckets.BucketErrorCode;
import messif.buckets.BucketStorageException;
import messif.buckets.LocalBucket;
import messif.buckets.impl.MemoryStorageBucket;
import messif.objects.LocalAbstractObject;
import messif.objects.util.AbstractObjectIterator;
import messif.operations.query.GetAlgorithmInfoOperation;
import messif.operations.data.BulkInsertOperation;
import messif.operations.data.DeleteByLocatorOperation;
import messif.operations.data.DeleteOperation;
import messif.operations.data.InsertOperation;
import messif.operations.QueryOperation;
import messif.operations.query.GetObjectCountOperation;

/**
 * Parallel implementation of the naive sequential scan algorithm.
 * Several buckets are used to store data in a round-robin fashion
 * using the {@link InsertOperation}. Then, each {@link messif.operations.QueryOperation}
 * is executed on each of the buckets in parallel.
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class ParallelSequentialScan extends Algorithm implements NavigationDirectory<QueryOperation<?>> {
    /** class id for serialization */
    static final long serialVersionUID = 1L;

    //****************** Attributes ******************//

    /** Instances of bucket where all the objects are stored */
    private final List<LocalBucket> buckets;

    /** Index of the bucket that receives next inserted object */
    private int insertBucket;


    //****************** Constructors ******************//

    /**
     * Creates a new instance of ParallelSequentialScan access structure with specific bucket class.
     * Additional parameters for the bucket class constructor can be passed.
     *
     * @param parallelization the number of parallel buckets to create
     * @param bucketClass the class of the storage bucket
     * @param bucketClassParams additional parameters for the bucket class constructor in the name->value form
     * @throws IllegalArgumentException if <ul><li>the provided bucketClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the bucketClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of bucketClass is not accessible</li>
     *                                         <li>the constructor of bucketClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "Parallel SequantialScan Access Structure", arguments = {"parallelization", "bucket class", "bucket class params"})
    public ParallelSequentialScan(int parallelization, Class<? extends LocalBucket> bucketClass, Map<String, Object> bucketClassParams) throws IllegalArgumentException {
        super("ParallelSequentialScan");

        // Check the parallelization parameter
        if (parallelization < 1)
            throw new IllegalArgumentException("Parallelization argument must be at least 1");

        // Create empty buckets (using the provided bucket class and parameters)
        buckets = new ArrayList<LocalBucket>(parallelization);
        for (int i = 0; i < parallelization; i++)
            buckets.add(BucketDispatcher.createBucket(bucketClass, Long.MAX_VALUE, Long.MAX_VALUE, 0, true, bucketClassParams));
        insertBucket = 0;
        setOperationsThreadPool(Executors.newFixedThreadPool(buckets.size()));
    }

    /**
     * Creates a new instance of ParallelSequentialScan access structure with specific bucket class.
     *
     * @param parallelization the number of parallel buckets to create
     * @param bucketClass the class of the storage bucket
     * @throws IllegalArgumentException if <ul><li>the provided bucketClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the bucketClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of bucketClass is not accessible</li>
     *                                         <li>the constructor of bucketClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "Parallel SequantialScan Access Structure", arguments = {"parallelization", "bucket class"})
    public ParallelSequentialScan(int parallelization, Class<? extends LocalBucket> bucketClass) throws IllegalArgumentException {
        this(parallelization, bucketClass, null);
    }

    /**
     * Creates a new instance of ParallelSequentialScan access structure with {@link MemoryStorageBucket} as the storage class.
     *
     * @param parallelization the number of parallel buckets to create
     * @throws IllegalArgumentException if <ul><li>the provided bucketClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the bucketClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of bucketClass is not accessible</li>
     *                                         <li>the constructor of bucketClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "Parallel SequantialScan Access Structure", arguments = {"parallelization"})
    public ParallelSequentialScan(int parallelization) throws IllegalArgumentException {
        this(parallelization, MemoryStorageBucket.class);
    }

    @Override
    public void finalize() throws Throwable {
        for (LocalBucket localBucket : buckets)
            localBucket.finalize();
        super.finalize();
    }

    @Override
    public void destroy() throws Throwable {
        for (LocalBucket localBucket : buckets)
            localBucket.finalize();
        // Do not call super.destroy(), since algorithm needs to differentiate between finalizing and destroying
    }


    //****************** Algorithm info ******************//

    /**
     * Method for processing {@link GetAlgorithmInfoOperation}.
     * The processing will fill the algorithm info with this
     * algorithm {@link #toString() toString()} value.
     * @param operation the operation to process
     */
    public void algorithmInfo(GetAlgorithmInfoOperation operation) {
        operation.addToAnswer(toString());
        operation.endOperation();
    }


    //****************** Object count ******************//

    /**
     * Method for processing {@link GetObjectCountOperation}.
     * The processing will fill the operation with the number of objects
     * stored in this algorithm.
     * @param operation the operation to process
     */
    public void objectCount(GetObjectCountOperation operation) {
        int objectCount = 0;
        for (LocalBucket bucket : buckets)
            objectCount += bucket.getObjectCount();
        operation.addToAnswer(objectCount);
        operation.endOperation();
    }


    //****************** Insert operation ******************//

    /**
     * Inserts a new object.
     * @param operation the insert operation which carries the object to be inserted.
     */
    public void insert(InsertOperation operation) {
        try {
            processObjectInsert(Collections.singleton(operation.getInsertedObject()));
            operation.endOperation();
        } catch (BucketStorageException e) {
            operation.endOperation(e.getErrorCode());
        }
    }

    /**
     * Inserts multiple new objects.
     * @param operation the bulk-insert operation which carries the objects to be inserted.
     */
    public void insert(BulkInsertOperation operation) {
        try {
            processObjectInsert(operation.getInsertedObjects());
            operation.endOperation();
        } catch (BucketStorageException e) {
            operation.endOperation(e.getErrorCode());
        }
    }

    /**
     * Processes the insertion of objects into buckets.
     * @param objects the collection of objects to insert
     * @throws BucketStorageException if there was an error inserting an object into internal buckets
     */
    protected synchronized void processObjectInsert(Collection<? extends LocalAbstractObject> objects) throws BucketStorageException {
        for (LocalAbstractObject object : objects) {
            buckets.get(insertBucket).addObject(object);
            insertBucket = (insertBucket + 1) % buckets.size();
        }
    }


    //****************** Delete operation ******************//

    /**
     * Deletes an object.
     * 
     * @param operation the delete operation which specifies the object to be deleted.
     * @throws BucketStorageException if the low occupation limit is reached when deleting object
     */
    public void delete(DeleteOperation operation) throws BucketStorageException {
        int deleted = 0;
        int limit = operation.getDeleteLimit();
        for (LocalBucket bucket : buckets) {
            deleted += bucket.deleteObject(operation.getDeletedObject(), limit == 0 ? 0 : limit - deleted);
            if (limit > 0 && deleted >= limit)
                break;
        }
        if (deleted > 0)
            operation.endOperation();
        else
            operation.endOperation(BucketErrorCode.OBJECT_NOT_FOUND);
    }

    /**
     * Deletes objects by locators.
     *
     * @param operation the delete operation which specifies the locators of objects to be deleted
     * @throws BucketStorageException if the low occupation limit is reached when deleting object
     */
    public void delete(DeleteByLocatorOperation operation) throws BucketStorageException {
        int deleted = 0;
        for (LocalBucket bucket : buckets) {
            try {
                AbstractObjectIterator<LocalAbstractObject> bucketIterator = bucket.getAllObjects();
                while (!operation.isLimitReached()) {
                    LocalAbstractObject obj = bucketIterator.getObjectByAnyLocator(operation.getLocators(), false); // Throws exception that exits the cycle
                    bucketIterator.remove();
                    operation.addDeletedObject(obj);
                    deleted++;
                }
            } catch (NoSuchElementException ignore) {
            }
        }
        if (deleted > 0)
            operation.endOperation();
        else
            operation.endOperation(BucketErrorCode.OBJECT_NOT_FOUND);
    }


    //****************** Query processing thread implementation ******************//

    @Override
    public AsynchronousNavigationProcessor<? extends QueryOperation<?>> getNavigationProcessor(QueryOperation<?> operation) {
        return new BucketQueryOperationNavigationProcessor<QueryOperation<?>>(operation, false, buckets);
    }


    //****************** Deserialization ******************//

    /**
     * Read the serialized algorithm from an object stream.
     * @param in the object stream from which to read the disk storage
     * @throws IOException if there was an I/O error during deserialization
     * @throws ClassNotFoundException if there was an unknown object in the stream
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        setOperationsThreadPool(Executors.newFixedThreadPool(buckets.size()));
    }


    //****************** Information string ******************//

    /**
     * Shows the information about this algorithm.
     * @return the information about this algorithm
     */
    @Override
    public String toString() {
        StringBuffer rtv;
        String lineSeparator = System.getProperty("line.separator", "\n");
        
        rtv = new StringBuffer();
        rtv.append("Algorithm: ").append(getName()).append(lineSeparator);
        rtv.append("Bucket Class: ").append(buckets.get(0).getClass().getName()).append(lineSeparator);
        long occupation = 0;
        int objectCount = 0;
        for (LocalBucket bucket : buckets) {
            occupation += bucket.getOccupation();
            objectCount += bucket.getObjectCount();
        }
        rtv.append("Number of buckets (threads): ").append(buckets.size()).append(lineSeparator);
        rtv.append("Bucket Occupation: ").append(occupation).append(" bytes").append(lineSeparator);
        rtv.append("Bucket Occupation: ").append(objectCount).append(" objects").append(lineSeparator);
        
        return rtv.toString();
    }
}
