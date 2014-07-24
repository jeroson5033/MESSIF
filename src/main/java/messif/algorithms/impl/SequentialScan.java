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

import java.util.Map;
import java.util.NoSuchElementException;
import messif.algorithms.Algorithm;
import messif.buckets.BucketDispatcher;
import messif.buckets.BucketErrorCode;
import messif.buckets.BucketStorageException;
import messif.buckets.CapacityFullException;
import messif.buckets.LocalBucket;
import messif.buckets.impl.MemoryStorageBucket;
import messif.objects.util.AbstractObjectList;
import messif.objects.util.AbstractObjectIterator;
import messif.objects.LocalAbstractObject;
import messif.objects.PrecomputedDistancesFixedArrayFilter;
import messif.operations.query.GetAlgorithmInfoOperation;
import messif.operations.data.BulkInsertOperation;
import messif.operations.data.DeleteByLocatorOperation;
import messif.operations.data.DeleteOperation;
import messif.operations.data.InsertOperation;
import messif.operations.QueryOperation;
import messif.operations.RankingSingleQueryOperation;
import messif.operations.query.GetObjectCountOperation;

/**
 * Implementation of the naive sequential scan algorithm.
 *
 * It uses one bucket to store objects and performs operations on the bucket.
 * It also supports pivot-based filtering. The pivots can be specified in a constructor.
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class SequentialScan extends Algorithm {
    /** class id for serialization */
    private static final long serialVersionUID = 1L;

    /** One instance of bucket where all objects are stored */
    protected final LocalBucket bucket;

    /** A list of fixed pivots used for filtering */
    protected final AbstractObjectList<LocalAbstractObject> pivots;

    /** Flag controlling the usage of PrecomputedDistancesFixedArrayFilter -- whether distances are set or appended (see the constructor below for details) */
    protected final boolean pivotDistsValidIfGiven;

    /**
     * Creates a new instance of SequantialScan access structure with the given bucket and filtering pivots.
     *
     * @param bucket the bucket used for the sequential scan
     * @param pivotIter the iterator from which the fixed pivots will be read
     * @param pivotCount the number of pivots to read from the iterator
     * @param pivotDistsValidIfGiven the flag which controls whether the already associated distances to pivots with new objects are valid or not; if so, they are used without computing and storing them again
     */
    public SequentialScan(LocalBucket bucket, AbstractObjectIterator<LocalAbstractObject> pivotIter, int pivotCount, boolean pivotDistsValidIfGiven) {
        super("SequentialScan");
        
        // Create an empty bucket (using the provided bucket class and parameters)
        this.bucket = bucket;
        
        // Get the fixed pivots
        if (pivotCount > 0 && pivotIter != null) {
            pivots = new AbstractObjectList<LocalAbstractObject>();
            for (int i = 0; i < pivotCount; i++)
                pivots.add(pivotIter.next());
        } else pivots = null;
        
        // Precomputed distances already associated with newly inserted objects are valid or not.
        // If there are no precomputed distances stored at new objects, they are computed, of course.
        this.pivotDistsValidIfGiven = pivotDistsValidIfGiven;
    }

    /**
     * Creates a new instance of SequantialScan access structure with the given bucket.
     *
     * @param bucket the bucket used for the sequential scan
     */
    public SequentialScan(LocalBucket bucket) {
        this(bucket, null, 0, true);
    }

    /**
     * Creates a new instance of SequantialScan access structure with specific bucket class and filtering pivots.
     * Additional parameters for the bucket class constructor can be passed.
     *
     * @param bucketClass the class of the storage bucket
     * @param bucketClassParams additional parameters for the bucket class constructor in the name->value form
     * @param pivotIter the iterator from which the fixed pivots will be read
     * @param pivotCount the number of pivots to read from the iterator
     * @param pivotDistsValidIfGiven the flag which controls whether the already associated distances to pivots with new objects are valid or not; if so, they are used without computing and storing them again
     * @throws CapacityFullException if the maximal number of buckets is already allocated
     * @throws InstantiationException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                   <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                   <li>the correct constructor of storageClass is not accessible</li>
     *                                   <li>the constructor of storageClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "SequentialScan Access Structure", arguments = {"bucket class", "bucket class params", "pivots", "pivot count", "pivotDistsValidIfGiven"})
    public SequentialScan(Class<? extends LocalBucket> bucketClass, Map<String, Object> bucketClassParams, AbstractObjectIterator<LocalAbstractObject> pivotIter, int pivotCount, boolean pivotDistsValidIfGiven) throws CapacityFullException, InstantiationException {
        this(BucketDispatcher.createBucket(bucketClass, Long.MAX_VALUE, Long.MAX_VALUE, 0, true, bucketClassParams), pivotIter, pivotCount, pivotDistsValidIfGiven);
    }

    /**
     * Creates a new instance of SequantialScan access structure with specific bucket class and filtering pivots.
     *
     * @param bucketClass The class of the storage bucket
     * @param pivotIter   The iterator from which the fixed pivots will be read
     * @param pivotCount  The number of pivots to read from the iterator
     * @param pivotDistsValidIfGiven The flag which controls whether the already associated distances to pivots with new objects are valid or not. If so, they are used without computing and storing them again.
     * @throws CapacityFullException if the maximal number of buckets is already allocated
     * @throws InstantiationException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                   <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                   <li>the correct constructor of storageClass is not accessible</li>
     *                                   <li>the constructor of storageClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "SequentialScan Access Structure", arguments = {"bucket class", "pivots", "pivot count", "pivotDistsValidIfGiven"})
    public SequentialScan(Class<? extends LocalBucket> bucketClass, AbstractObjectIterator<LocalAbstractObject> pivotIter, int pivotCount, boolean pivotDistsValidIfGiven) throws CapacityFullException, InstantiationException {
        this(bucketClass, null, pivotIter, pivotCount, pivotDistsValidIfGiven);
    }

    /**
     * Creates a new instance of SequantialScan access structure with specific bucket class.
     * Additional parameters for the bucket class constructor can be passed.
     *
     * @param bucketClass The class of the storage bucket
     * @param bucketClassParams additional parameters for the bucket class constructor in the name->value form
     * @throws CapacityFullException if the maximal number of buckets is already allocated
     * @throws InstantiationException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                   <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                   <li>the correct constructor of storageClass is not accessible</li>
     *                                   <li>the constructor of storageClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "SequentialScan Access Structure", arguments = {"bucket class", "bucket class params"})
    public SequentialScan(Class<? extends LocalBucket> bucketClass, Map<String, Object> bucketClassParams) throws CapacityFullException, InstantiationException {
        this(bucketClass, bucketClassParams, null, 0, false);
    }

    /**
     * Creates a new instance of SequantialScan access structure with specific bucket class.
     *
     * @param bucketClass The class of the storage bucket
     * @throws CapacityFullException if the maximal number of buckets is already allocated
     * @throws InstantiationException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                   <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                   <li>the correct constructor of storageClass is not accessible</li>
     *                                   <li>the constructor of storageClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "SequentialScan Access Structure", arguments = {"bucket class"})
    public SequentialScan(Class<? extends LocalBucket> bucketClass) throws CapacityFullException, InstantiationException {
        this(bucketClass, null);
    }

    /**
     * Creates a new instance of SequantialScan access structure with the default MemoryStorageBucket class.
     *
     * @throws CapacityFullException if the maximal number of buckets is already allocated
     * @throws InstantiationException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                   <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                   <li>the correct constructor of storageClass is not accessible</li>
     *                                   <li>the constructor of storageClass has failed</li></ul>
     */
    @Algorithm.AlgorithmConstructor(description = "SequentialScan Access Structure", arguments = {})
    public SequentialScan() throws CapacityFullException, InstantiationException {
        this(MemoryStorageBucket.class);
    }


    //******* PIVOT OPERATIONS *************************************//

    /**
     * Add precomputed distances to a given object.
     * Distance to all pivots is measured and stored into {@link PrecomputedDistancesFixedArrayFilter}.
     *
     * @param object the object to add the distances to
     */
    protected void addPrecompDist(LocalAbstractObject object) {
        PrecomputedDistancesFixedArrayFilter precompDist = object.getDistanceFilter(PrecomputedDistancesFixedArrayFilter.class);
        if (precompDist == null || !pivotDistsValidIfGiven) {
            // No precomputed distance associated or we are requested to add the distances to pivot on our own.
            if (precompDist == null)
                precompDist = new PrecomputedDistancesFixedArrayFilter(object);
            precompDist.addPrecompDist(pivots, object);
        }
    }

    @Override
    public void finalize() throws Throwable {
        bucket.finalize();
        super.finalize();
    }

    @Override
    public void destroy() throws Throwable {
        bucket.destroy();
        // Do not call super.destroy(), since algorithm needs to differentiate between finalizing and destroying
    }


    //******* ALGORITHM INFO OPERATION *************************************//

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

    //******* OBJECT COUNT OPERATION *************************************//

    /**
     * Method for processing {@link GetObjectCountOperation}.
     * The processing will fill the operation with the number of objects
     * stored in this algorithm.
     * @param operation the operation to process
     */
    public void objectCount(GetObjectCountOperation operation) {
        operation.addToAnswer(bucket.getObjectCount());
        operation.endOperation();
    }


    //******* INSERT OPERATION *************************************//

    /**
     * Inserts a new object.
     * 
     * @param operation Operation of insert which carries the object to be inserted.
     * @throws CapacityFullException if the hard capacity of the bucket is exceeded
     */
    public void insert(InsertOperation operation) throws CapacityFullException {
        // If pivot-based filtering is required, store the distances from pivots.
        if (pivots != null)
            addPrecompDist(operation.getInsertedObject());

        // Add the new object
        operation.endOperation(bucket.addObjectErrCode(operation.getInsertedObject()));
    }

    /**
     * Bulk insertion. Inserts a list of new objects.
     * 
     * @param operation The operation of bulk insert which carries the objects to be inserted.
     * @throws BucketStorageException if the hard capacity of the bucket is exceeded
     */
    public void bulkInsert(BulkInsertOperation operation) throws BucketStorageException {
        // If pivot-based filtering is required, store the distances from pivots.
        if (pivots != null)
            for (LocalAbstractObject obj : operation.getInsertedObjects())
                addPrecompDist(obj);

        // Add the new objects
        bucket.addObjects(operation.getInsertedObjects());
        operation.endOperation();
    }


    //******* DELETE OPERATION *************************************//

    /**
     * Deletes an object.
     *
     * @param operation The operation which specifies the object to be deleted.
     * @throws BucketStorageException if the low occupation limit is reached when deleting object
     */
    public void delete(DeleteOperation operation) throws BucketStorageException {
        int deleted = bucket.deleteObject(operation.getDeletedObject(), operation.getDeleteLimit());
        if (deleted > 0)
            operation.endOperation();
        else
            operation.endOperation(BucketErrorCode.OBJECT_NOT_FOUND);
    }

    /**
     * Deletes objects by locators.
     *
     * @param operation the operation which specifies the locators of objects to be deleted
     * @throws BucketStorageException if the low occupation limit is reached when deleting object
     */
    public void delete(DeleteByLocatorOperation operation) throws BucketStorageException {
        int deleted = 0;
        AbstractObjectIterator<LocalAbstractObject> bucketIterator = bucket.getAllObjects();
        try {
            while (!operation.isLimitReached()) {
                LocalAbstractObject obj = bucketIterator.getObjectByAnyLocator(operation.getLocators(), false); // Throws exception that exits the cycle
                bucketIterator.remove();
                operation.addDeletedObject(obj);
                deleted++;
            }
        } catch (NoSuchElementException ignore) {
        }

        if (deleted > 0)
            operation.endOperation();
        else
            operation.endOperation(BucketErrorCode.OBJECT_NOT_FOUND);
    }


    //******* SEARCH ALGORITHMS ************************************//

    /**
     * Evaluates a ranking single query object operation on this algorithm.
     * Note that the operation is evaluated sequentially on all objects of this algorithm.
     * @param operation the operation to evaluate
     */
    public void singleQueryObjectSearch(RankingSingleQueryOperation operation) {
        // If pivot-based filtering is required, store the distances from pivots.
        if (pivots != null)
            addPrecompDist(operation.getQueryObject());
        bucket.processQuery(operation);
        operation.endOperation();
    }

    /**
     * Performs a generic query operation.
     * Note that this method cannot provide precomputed distances.
     * 
     * @param operation the query operation which is to be executed and which will received the result list.
     * @throws CloneNotSupportedException if the operation does not support cloning (and thus cannot be used in parallel)
     * @throws InterruptedException if the processing thread was interrupted during processing
     */
    public void search(QueryOperation<?> operation) throws CloneNotSupportedException, InterruptedException {
        bucket.processQuery(operation);
        operation.endOperation();
    }

    /**
     * Converts the object to a string representation
     * @return String representation of this algorithm
     */
    @Override
    public String toString() {
        StringBuffer rtv;
        String lineSeparator = System.getProperty("line.separator", "\n");
        
        rtv = new StringBuffer();
        rtv.append("Algorithm: ").append(getName()).append(lineSeparator);
        rtv.append("Bucket Class: ").append(bucket.getClass().getName()).append(lineSeparator);
        rtv.append("Bucket Occupation: ").append(bucket.getOccupation()).append(" bytes").append(lineSeparator);
        rtv.append("Bucket Occupation: ").append(bucket.getObjectCount()).append(" objects").append(lineSeparator);
        
        return rtv.toString();
    }
}
