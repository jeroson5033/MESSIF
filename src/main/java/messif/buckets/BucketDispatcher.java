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
package messif.buckets;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import messif.pivotselection.AbstractPivotChooser;


/**
 * This class is a dispatcher for maintaining a set of local buckets.
 *
 * Kept buckets can be accessed using unique bucket identifiers (BIDs).
 *
 * New buckets can be created using the {@link #createBucket} method - the unique ID is assigned automatically.
 * To create a bucket, a specific bucket implementation class, capacity settings and additional class-specific parameters are needed.
 * They are either passed to the {@link #createBucket createBucket} method, or the dispatcher's default values are used.
 * Automatic pivot choosers can be created for new buckets - see {@link #setAutoPivotChooser}.
 *
 * To remove a bucket from the dispatcher, use {@link #removeBucket}. Note that
 * objects remain inside the bucket, just the bucket will be no longer maintained
 * by this dispatcher.
 *
 * @see LocalBucket
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class BucketDispatcher implements Serializable, TemporaryCloseable {
    /** class serial id for serialization */
    private static final long serialVersionUID = 2L;

    /** Logger for the bucket dispatcher */
    protected static final Logger log = Logger.getLogger(BucketDispatcher.class.getName());


    //****************** Bucket dispatcher data ******************//

    /** The buckets maintained by this dispatcher organized in hashtable with bucket IDs as keys */
    private final Map<Integer,LocalBucket> buckets = new ConcurrentHashMap<Integer,LocalBucket>();

    /** Maximal number of buckets maintained by this dispatcher */
    private final int maxBuckets;

    /** Automatic bucket ID generator */
    private final AtomicInteger nextBucketID = new AtomicInteger(1);

    /** The ID of buckets that do not belong to a dispatcher */
    public static final int UNASSIGNED_BUCKET_ID = 0;

    /** Default bucket hard capacity for newly created buckets */
    private long bucketCapacity;

    /** Default bucket soft capacity for newly created buckets */
    private long bucketSoftCapacity;

    /** Default bucket hard low-occupation for newly created buckets */
    private long bucketLowOccupation;

    /** Default flag whether to store occupation & capacity in bytes (<tt>true</tt>) or number of objects (<tt>false</tt>) for newly created buckets */
    private boolean bucketOccupationAsBytes;

    /** Default class for newly created buckets */
    private Class<? extends LocalBucket> defaultBucketClass;

    /** Default parameters for newly created buckets with default bucket class */
    private Map<String, Object> defaultBucketClassParams;


    //****************** Constructors ******************//

    /**
     * Creates a new instance of BucketDispatcher with full specification of default values.
     *
     * @param maxBuckets the maximal number of buckets maintained by this dispatcher
     * @param bucketCapacity the default bucket hard capacity for newly created buckets
     * @param bucketSoftCapacity the default bucket soft capacity for newly created buckets
     * @param bucketLowOccupation the default bucket hard low-occupation for newly created buckets
     * @param bucketOccupationAsBytes the default flag whether to store occupation & capacity in bytes (<tt>true</tt>) or number of objects (<tt>false</tt>) for newly create buckets
     * @param defaultBucketClass the default class for newly created buckets
     * @param defaultBucketClassParams the default parameters for newly created buckets with default bucket class
     */
    public BucketDispatcher(int maxBuckets, long bucketCapacity, long bucketSoftCapacity, long bucketLowOccupation, boolean bucketOccupationAsBytes, Class<? extends LocalBucket> defaultBucketClass, Map<String, Object> defaultBucketClassParams) {
        this.maxBuckets = maxBuckets;
        this.bucketCapacity = bucketCapacity;
        this.bucketSoftCapacity = bucketSoftCapacity;
        this.bucketLowOccupation = bucketLowOccupation;
        this.bucketOccupationAsBytes = bucketOccupationAsBytes;
        this.defaultBucketClass = defaultBucketClass;
        this.defaultBucketClassParams = defaultBucketClassParams;
    }

    /**
     * Creates a new instance of BucketDispatcher with full specification of default values.
     * No additional parameters for the default bucket class are specified.
     *
     * @param maxBuckets the maximal number of buckets maintained by this dispatcher
     * @param bucketCapacity the default bucket hard capacity for newly created buckets
     * @param bucketSoftCapacity the default bucket soft capacity for newly created buckets
     * @param bucketLowOccupation the default bucket hard low-occupation for newly created buckets
     * @param bucketOccupationAsBytes the default flag whether to store occupation & capacity in bytes (<tt>true</tt>) or number of objects (<tt>false</tt>) for newly create buckets
     * @param defaultBucketClass the default class for newly created buckets
     */
    public BucketDispatcher(int maxBuckets, long bucketCapacity, long bucketSoftCapacity, long bucketLowOccupation, boolean bucketOccupationAsBytes, Class<? extends LocalBucket> defaultBucketClass) {
        this(maxBuckets, bucketCapacity, bucketSoftCapacity, bucketLowOccupation, bucketOccupationAsBytes, defaultBucketClass, null);
    }

    /**
     * Creates a new instance of BucketDispatcher only with maximal capacity specification.
     * The soft capacity and low-occupation limits are not set. The occupation and capacity
     * is counted in bytes.
     *
     * @param maxBuckets the maximal number of buckets maintained by this dispatcher
     * @param bucketCapacity the default bucket hard capacity for newly created buckets
     * @param defaultBucketClass the default class for newly created buckets
     */
    public BucketDispatcher(int maxBuckets, long bucketCapacity, Class<? extends LocalBucket> defaultBucketClass) {
        this(maxBuckets, bucketCapacity, bucketCapacity, 0, true, defaultBucketClass);
    }

    /**
     * Finalize all buckets managed by this dispatcher.
     * @throws Throwable if there was an error during releasing resources
     */
    @Override
    @SuppressWarnings({"FinalizeNotProtected", "FinalizeCalledExplicitly"})
    public void finalize() throws Throwable {
        Throwable posponedThrowable = null;
        for (LocalBucket bucket : getAllBuckets())
            try {
                bucket.finalize();
            } catch (Throwable e) {
                posponedThrowable = e;
            }
        if (posponedThrowable != null)
            throw posponedThrowable;
        super.finalize();
    }

    /**
     * Destroys all buckets managed by this dispatcher.
     * @throws Throwable if there was an error during destroying buckets
     */
    public void destroy() throws Throwable {
        Throwable posponedThrowable = null;
        for (LocalBucket bucket : getAllBuckets())
            try {
                bucket.destroy();
            } catch (Throwable e) {
                posponedThrowable = e;
            }
        if (posponedThrowable != null)
            throw posponedThrowable;
    }


    //****************** Automatic pivot choosers ******************//

    /** The class of pivot chooser that is automatically created for newly created buckets */
    private Class<? extends AbstractPivotChooser> autoPivotChooserClass = null;

    /** The pivot chooser instance that chooses pivots for all the buckets in this dispatcher */
    private AbstractPivotChooser autoPivotChooserInstance = null;

    /** The hash table of pivot choosers that are assigned to buckets of this dispatcher */
    private final Map<LocalBucket, AbstractPivotChooser> createdPivotChoosers = Collections.synchronizedMap(new HashMap<LocalBucket, AbstractPivotChooser>());

    /**
     * Returns pivot chooser that was automatically created for a bucket of this dispatcher.
     *
     * @param bucketID the ID of the bucket for which to get the pivot chooser
     * @return pivot chooser that was automatically created for the specified bucket
     */
    public AbstractPivotChooser getAutoPivotChooser(int bucketID) {
        return createdPivotChoosers.get(getBucket(bucketID));
    }

    /**
     * Set the class of pivot chooser that will be created whenever a bucket is created by this dispatcher.
     * @param autoPivotChooserClass the class of the pivot chooser to create
     * @throws IllegalArgumentException if the specified class is abstract or does not have a public nullary constructor
     */
    public void setAutoPivotChooser(Class<? extends AbstractPivotChooser> autoPivotChooserClass) throws IllegalArgumentException {
        // Check if a public nullary constructor exists and the class is not abstract
        if (autoPivotChooserClass != null)
            try {
                if (Modifier.isAbstract(autoPivotChooserClass.getModifiers()))
                    throw new IllegalArgumentException("Cannot set auto pivot chooser " + autoPivotChooserClass.getName() + ": the class is abstract");
                autoPivotChooserClass.getConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Cannot set auto pivot chooser " + autoPivotChooserClass.getName() + ": the class does not have a public nullary constructor");
            }

        synchronized (createdPivotChoosers) {
            this.autoPivotChooserClass = autoPivotChooserClass;
            this.autoPivotChooserInstance = null;
        }
    }

    /**
     * Set the pivot chooser instance that chooses pivots for all the buckets in this dispatcher.
     * @param autoPivotChooserInstance the pivot chooser instance
     */
    public void setAutoPivotChooser(AbstractPivotChooser autoPivotChooserInstance) {
        synchronized (createdPivotChoosers) {
            this.autoPivotChooserInstance = autoPivotChooserInstance;
            this.autoPivotChooserClass = null;
        }
    }

    /**
     * Returns the class of the pivot chooser that is currently used for buckets in this dispatcher.
     * @return the class of the pivot chooser that is currently used for buckets in this dispatcher
     */
    public Class<? extends AbstractPivotChooser> getAutoPivotChooserClass() {
        synchronized (createdPivotChoosers) {
            if (autoPivotChooserInstance != null)
                return autoPivotChooserInstance.getClass();
            return autoPivotChooserClass;
        }
    }

    /**
     * Creates a new pivot chooser for the provided bucket.
     * If the class for autoPivotChooser was specified, a new instance is created, otherwise
     * the pivot chooser instance for the whole dispatcher is used.
     * The specified bucket is registered as sample set provider for the pivot chooser
     * The bucket is also associated with the chooser, so that the {@link #getAutoPivotChooser} will
     * return it for the bucket.
     *
     * @param bucket the bucket for which to create pivot chooser
     * @return the newly created pivot chooser or the whole dispatcher pivot chooser instance; <tt>null</tt> is returned
     *         if either the class/instance of autoPivotChooser is not specified in this dispatcher or there was an error creating
     *         a new pivot chooser
     */
    protected AbstractPivotChooser createAutoPivotChooser(LocalBucket bucket) {
        synchronized (createdPivotChoosers) {
            AbstractPivotChooser rtv;
            
            // Create new instance of auto pivot chooser class
            if (autoPivotChooserClass != null) {
                try {
                    // Constructor with bucket parameter not found, try no-param constructor
                    rtv = autoPivotChooserClass.newInstance();
                } catch (Exception e) {
                    log.log(Level.WARNING, "Can''t create automatic pivot chooser {0}: {1}", new Object[]{autoPivotChooserClass.toString(), e.toString()});
                    return null;
                }
            } else if (autoPivotChooserInstance != null) {
                // Use existing instance of pivot chooser (one for all buckets in this dispatcher)
                rtv = autoPivotChooserInstance;
            } else return null;
            
            // Register the new bucket as sample provider for the pivot chooser
            rtv.registerSampleProvider(bucket);

            // Register the pivot chooser as filter if it implements BucketFilterInterface
            if (rtv instanceof BucketFilter)
                bucket.registerFilter((BucketFilter)rtv);
            
            // Assiciate the pivot chooser with the bucket
            createdPivotChoosers.put(bucket, rtv);
            
            return rtv;   
        }
    }


    // ******************  Parameter setters  ******************* //

    /**
     * Set bucket capacity for all new buckets.
     * @param bucketCapacity new hard capacity.
     */
    public void setBucketCapacity(long bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
    }

    /**
     * Set parameter "low occupation" for all new buckets
     * @param bucketLowOccupation new low occupation.
     */
    public void setBucketLowOccupation(long bucketLowOccupation) {
        this.bucketLowOccupation = bucketLowOccupation;
    }

    /**
     * Set parameter {@link #bucketOccupationAsBytes} for all new buckets.
     * @param bucketOccupationAsBytes new value for parameter {@link #bucketOccupationAsBytes}.
     */
    public void setBucketOccupationAsBytes(boolean bucketOccupationAsBytes) {
        this.bucketOccupationAsBytes = bucketOccupationAsBytes;
    }

    /**
     * Set new soft capacity for all new buckets.
     * @param bucketSoftCapacity new soft capacity parameter
     */
    public void setBucketSoftCapacity(long bucketSoftCapacity) {
        this.bucketSoftCapacity = bucketSoftCapacity;
    }

    /**
     * Set default class for all new buckets
     * @param defaultBucketClass new bucket default class.
     */
    public void setDefaultBucketClass(Class<? extends LocalBucket> defaultBucketClass) {
        this.defaultBucketClass = defaultBucketClass;
    }

    /**
     * New parameters for all new default buckets
     * @param defaultBucketClassParams new parameters for default buckets
     */
    public void setDefaultBucketClassParams(Map<String, Object> defaultBucketClassParams) {
        this.defaultBucketClassParams = new HashMap<String, Object>(defaultBucketClassParams);
    }

    /**
     * Set the soft capacity for all buckets registered by this dispatcher.
     * @param bucketSoftCapacity new bucket soft capacity for the existing buckets
     */
    public void setAllBucketSoftCapacity(long bucketSoftCapacity) {
        for (LocalBucket bucket : buckets.values()) {
            bucket.setSoftCapacity(bucketSoftCapacity);
        }
    }

    /**
     * Set the low occupation for all buckets registered by this dispatcher.
     * @param bucketLowOccupation new low occupation.
     */
    public void setAllBucketLowOccupation(long bucketLowOccupation) {
        for (LocalBucket bucket : buckets.values()) {
            bucket.setLowOccupation(bucketLowOccupation);
        }
    }


    //****************** Info Methods *******************//

    /**
     * Returns the maximal number of buckets maintained by this dispatcher.
     * @return the maximal number of buckets maintained by this dispatcher
     */
    public int getMaxBuckets() {
        return maxBuckets;
    }

    /**
     * Returns the default hard capactity limit for new buckets.
     * @return the default hard capactity limit for new buckets
     */
    public long getBucketCapacity() {
        return bucketCapacity;
    }

    /**
     * Returns the default soft capactity limit for new buckets.
     * @return the default soft capactity limit for new buckets
     */
    public long getBucketSoftCapacity() {
        return bucketSoftCapacity;
    }

    /**
     * Returns the default hard low-occupation capactity limit for new buckets.
     * @return the default hard low-occupation capactity limit for new buckets
     */
    public long getBucketLowOccupation() {
        return bucketLowOccupation;
    }

    /**
     * Returns the default flag whether to compute occupation & capacity in bytes (<tt>true</tt>)
     * or number of objects (<tt>false</tt>) for new buckets.
     * @return <tt>true</tt> if the default is to compute occupation & capacity in bytes or <tt>false</tt> if it is computed in number of objects
     */
    public boolean getBucketOccupationAsBytes() {
        return bucketOccupationAsBytes;
    }

    /**
     * Returns the default class for newly created buckets.
     * @return the default class for newly created buckets
     */
    public Class<? extends LocalBucket> getDefaultBucketClass() {
        return defaultBucketClass;
    }

    /**
     * Returns the default parameters for newly created buckets with default bucket class.
     * @return the default parameters for newly created buckets with default bucket class
     */
    public Map<String, Object> getDefaultBucketClassParams() {
        return Collections.unmodifiableMap(defaultBucketClassParams);
    }

    /**
     * Returns the actual number of buckets maintained by this dispatcher.
     * @return the dispatcher's bucket count
     */
    public int getBucketCount() {
        return buckets.size();
    }

    /**
     * Returns number of buckets that exceed their soft-capacities.
     * @return number of buckets that exceed their soft-capacities
     */
    public int getOverloadedBucketCount() {
        int cnt = 0;
        for (LocalBucket b : buckets.values()) {
            if (b.isSoftCapacityExceeded())
                ++cnt;
        }
        return cnt;
    }

    /**
     * Returns the sum of occupations of all buckets maintained by this dispatcher.
     * @return the sum of occupations of all buckets
     */
    public long getOccupation() {
        long retVal = 0;
        for (LocalBucket bucket:buckets.values())
            retVal += bucket.getOccupation();
        return retVal;
    }

    /**
     * Returns the sum of object counts stored in all buckets maintained by this dispatcher.
     * @return the sum of object counts stored in all buckets
     */
    public int getObjectCount() {
        int retVal = 0;
        for (LocalBucket bucket:buckets.values())
            retVal += bucket.getObjectCount();
        return retVal;
    }


    //****************** Bucket access ******************//

    /**
     * Returns the set of bucket IDs maintained by this dispatcher.
     * @return the set of bucket IDs maintained by this dispatcher
     */
    public synchronized Set<Integer> getAllBucketIDs() {      
        return Collections.unmodifiableSet(buckets.keySet());
    }

    /**
     * Returns the collection of all buckets maintained by this dispatcher.
     * @return the collection of all buckets maintained by this dispatcher
     */
    public synchronized Collection<LocalBucket> getAllBuckets() {
        return Collections.unmodifiableCollection(buckets.values());
    }

    /**
     * Returns the bucket with the specified ID.
     *
     * @param bucketID the ID of the bucket to return
     * @return the bucket with the specified ID
     * @throws NoSuchElementException if there is no bucket associated with the specified ID in this dispatcher
     */
    public LocalBucket getBucket(int bucketID) throws NoSuchElementException {
        LocalBucket rtv = buckets.get(bucketID); 
        if (rtv == null) 
            throw new NoSuchElementException("Bucket ID " + bucketID + " doesn't exist.");
        
        return rtv;
    }


    //****************** Autoclosing disk buckets ******************//

    /** Internal thread used for periodical check for temporarily-closeable buckets */
    private TemporaryCloseableThread closeTeporarilyThread;

    @Override
    public boolean closeTemporarilyIfIdle(boolean resetAccessCounter) throws IOException {
        boolean ret = false;
        for (LocalBucket bucket : buckets.values()) {
            if (bucket instanceof TemporaryCloseable) {
                if (((TemporaryCloseable)bucket).closeTemporarilyIfIdle(resetAccessCounter))
                    ret = true;
            } else if (bucket.getModifiableIndex() instanceof TemporaryCloseable) {
                if (((TemporaryCloseable)bucket.getModifiableIndex()).closeTemporarilyIfIdle(resetAccessCounter))
                    ret = true;
            }
        }
        return ret;
    }

    /**
     * Update the period of the automatic temporary-closeable checking of the buckets.
     * @param period the new checking period in milliseconds;
     *          zero value means disable the checking
     */
    public void setTemporaryClosePeriod(long period) {
        if (period <= 0) {
            if (closeTeporarilyThread != null) {
                closeTeporarilyThread.interrupt();
                closeTeporarilyThread = null;
            }
        } else {
            if (closeTeporarilyThread != null) {
                closeTeporarilyThread.setPeriod(period);
            } else {
                closeTeporarilyThread = new TemporaryCloseableThread(period);
                closeTeporarilyThread.add(this);
                closeTeporarilyThread.start();
            }
        }
    }


    //****************** Bucket creation/deletion ******************//

    /**
     * Create new local bucket with specified storage class and storage capacity (different from default values).
     *
     * @param storageClass the class that represents the bucket implementation to use
     * @param capacity the hard capacity of the new bucket
     * @param softCapacity the soft capacity of the new bucket (soft &lt;= hard must hold otherwise hard is set to soft)
     * @param lowOccupation the low-occupation limit for the new bucket
     * @param occupationAsBytes flag whether the occupation (and thus all the limits) are in bytes or number of objects
     * @param storageClassParams additional parameters for creating a new instance of the storageClass
     * @return a new instance of the specified bucket class
     * @throws IllegalArgumentException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of storageClass is not accessible</li>
     *                                         <li>the constructor of storageClass has failed</li></ul>
     */
    public static LocalBucket createBucket(Class<? extends LocalBucket> storageClass, long capacity, long softCapacity, long lowOccupation, boolean occupationAsBytes, Map<String, Object> storageClassParams) throws IllegalArgumentException {
        // Update provided parameters to correct values
        if (softCapacity < 0) softCapacity = 0;
        if (capacity < softCapacity) capacity = softCapacity;
        
        // Create new bucket with specified capacity
        try {
            try {
                // Try bucket class internal factory first
                Method factoryMethod = storageClass.getDeclaredMethod("getBucket", long.class, long.class, long.class, boolean.class, Map.class);
                if (!Modifier.isStatic(factoryMethod.getModifiers()))
                    throw new IllegalArgumentException("Factory method 'getBucket' in " + storageClass + " is not static");
                if (!storageClass.isAssignableFrom(factoryMethod.getReturnType()))
                    throw new IllegalArgumentException("Factory method 'getBucket' in " + storageClass + " has wrong return type");
                return (LocalBucket)factoryMethod.invoke(null, capacity, softCapacity, lowOccupation, occupationAsBytes, storageClassParams);
            } catch (NoSuchMethodException ignore) {
                // Factory method doesn't exist, try class constructor
                return storageClass.getDeclaredConstructor(
                            long.class, long.class, long.class, boolean.class
                         ).newInstance(
                            capacity, softCapacity, lowOccupation, occupationAsBytes
                         );
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Storage " + storageClass + " lacks proper constructor: " + e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Storage " + storageClass + " constructor with capacity is unaccesible: " + e.getMessage(), e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Storage " + storageClass + " cannot be created because it is an abstract class", e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Storage " + storageClass + " constructor invocation failed: " + e.getCause(), e.getCause());
        }
    }

    /**
     * Create new local bucket with specified storage class and storage capacity (different from default values).
     *
     * @param storageClass the class that represents the bucket implementation to use
     * @param storageClassParams additional parameters for creating a new instance of the storageClass
     * @param capacity the hard capacity of the new bucket
     * @param softCapacity the soft capacity of the new bucket (soft &lt;= hard must hold otherwise hard is set to soft)
     * @param lowOccupation the low-occupation limit for the new bucket
     *
     * @return a new instance of the specified bucket class
     * @throws BucketStorageException if the maximal number of buckets is already allocated
     * @throws IllegalArgumentException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of storageClass is not accessible</li>
     *                                         <li>the constructor of storageClass has failed</li></ul>
     */
    public synchronized LocalBucket createBucket(Class<? extends LocalBucket> storageClass, Map<String, Object> storageClassParams, long capacity, long softCapacity, long lowOccupation) throws BucketStorageException, IllegalArgumentException {
        // Create new bucket with specified capacity
        return addBucket(createBucket(storageClass, capacity, softCapacity, lowOccupation, getBucketOccupationAsBytes(), storageClassParams));
    }

    /**
     * Create new local bucket with the default storage class and default storage capacity.
     *
     * @return a new instance of the default bucket class
     * @throws BucketStorageException if the maximal number of buckets is already allocated
     * @throws IllegalArgumentException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of storageClass is not accessible</li>
     *                                         <li>the constructor of storageClass has failed</li></ul>
     */
    public LocalBucket createBucket() throws BucketStorageException, IllegalArgumentException {
        return createBucket(defaultBucketClass, defaultBucketClassParams, bucketCapacity, bucketSoftCapacity, bucketLowOccupation);
    }

    /**
     * Create new local bucket with specified storage class and default storage capacity.
     * No additional parameters are passed to the constructor of the storageClass.
     *
     * @param storageClass the class that represents the bucket implementation to use
     *
     * @return a new instance of the specified bucket class
     * @throws BucketStorageException if the maximal number of buckets is already allocated
     * @throws IllegalArgumentException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of storageClass is not accessible</li>
     *                                         <li>the constructor of storageClass has failed</li></ul>
     */
    public LocalBucket createBucket(Class<? extends LocalBucket> storageClass) throws BucketStorageException, IllegalArgumentException {
        return createBucket(storageClass, null, bucketCapacity, bucketSoftCapacity, bucketLowOccupation);
    }

    /**
     * Create new local bucket with specified storage class and default storage capacity.
     * Additional parameters cat be specified for the constructor of the storageClass.
     *
     * @param storageClass the class that represents the bucket implementation to use
     * @param storageClassParams additional parameters for creating a new instance of the storageClass
     *
     * @return a new instance of the specified bucket class
     * @throws BucketStorageException if the maximal number of buckets is already allocated
     * @throws IllegalArgumentException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of storageClass is not accessible</li>
     *                                         <li>the constructor of storageClass has failed</li></ul>
     */
    public LocalBucket createBucket(Class<? extends LocalBucket> storageClass, Map<String, Object> storageClassParams) throws BucketStorageException, IllegalArgumentException {
        return createBucket(storageClass, storageClassParams, bucketCapacity, bucketSoftCapacity, bucketLowOccupation);
    }

    /**
     * Create new local bucket with default storage class and specified storage capacity.
     *
     * @param capacity the hard capacity of the new bucket
     * @param softCapacity the soft capacity of the new bucket (soft <= hard must hold otherwise hard is set to soft)
     * @param lowOccupation the low-occupation limit for the new bucket
     *
     * @return a new instance of the specified bucket class
     * @throws BucketStorageException if the maximal number of buckets is already allocated
     * @throws IllegalArgumentException if <ul><li>the provided storageClass is not a part of LocalBucket hierarchy</li>
     *                                         <li>the storageClass does not have a proper constructor (String,long,long)</li>
     *                                         <li>the correct constructor of storageClass is not accessible</li>
     *                                         <li>the constructor of storageClass has failed</li></ul>
     */
    public LocalBucket createBucket(long capacity, long softCapacity, long lowOccupation) throws BucketStorageException, IllegalArgumentException {
        return createBucket(defaultBucketClass, defaultBucketClassParams, capacity, softCapacity, lowOccupation);
    }

    /**
     * Add an existing bucket to this dispatcher.
     * A new unique ID is assigned to the bucket.
     *
     * @param bucket the bucket to add to this dispatcher
     * @return the added bucket
     * @throws IllegalStateException if the bucket is already maintained by another one
     * @throws BucketStorageException if the maximal number of buckets is already allocated
     */
    public synchronized LocalBucket addBucket(LocalBucket bucket) throws IllegalStateException, BucketStorageException {
        // Check capacity
        if (buckets.size() >= maxBuckets)
            throw new CapacityFullException();

        // Check if bucket has no ID
        if (!bucket.isBucketStandalone()) {
            if (bucket == getBucket(bucket.getBucketID()))
                return bucket; // Bucket is already present in this dispatcher, ignore addition silently
            else throw new IllegalStateException("Bucket " + bucket + " can't be added to bucket dispatcher, because it is already maintained by another one");
        }

        // Add the new bucket to collection
        bucket.setBucketID(nextBucketID.getAndIncrement());
        buckets.put(bucket.getBucketID(), bucket);
        
        // Create pivot chooser for the bucket
        createAutoPivotChooser(bucket);

        return bucket;
    }

    /**
     * Delete the bucket with specified ID from this dispatcher.
     * If destroying the bucket is not requested (i.e. {@code destroyBucket == false}),
     * the bucket will be no longer maintained by this dispatcher, but no objects
     * are deleted from the bucket.
     * @param bucketID the ID of the bucket to delete
     * @param destroyBucket if <tt>true</tt>, all the objects in the bucket are destroyed.
     * @return the bucket deleted
     * @throws NoSuchElementException if there is no bucket with the specified ID
     */
    public synchronized LocalBucket removeBucket(int bucketID, boolean destroyBucket) throws NoSuchElementException {
        LocalBucket bucket = buckets.remove(bucketID);
        if (bucket == null)
            throw new NoSuchElementException("Bucket ID " + bucketID + " doesn't exist.");

        // Remove auto-pivot chooser for the bucket
        createdPivotChoosers.remove(bucket);

        // Reset bucket ID and statistics
        bucket.setBucketID(UNASSIGNED_BUCKET_ID);
        try {
            if (destroyBucket)
                bucket.destroy();
        } catch (Throwable e) {
            // Log the exception but continue cleanly
            log.log(Level.WARNING, "Error during bucket clean-up, continuing", e);
        }

        return bucket;
    }

    /**
     * Delete the bucket with specified ID from this dispatcher.
     * Note that the bucket is {@link LocalBucket#destroy() destroyed},
     * i.e. all objects are deleted and
     * However, statistics for the bucket are destroyed.
     *
     * @param bucketID the ID of the bucket to delete
     * @throws NoSuchElementException if there is no bucket with the specified ID
     */
    public void removeBucket(int bucketID) throws NoSuchElementException {
        removeBucket(bucketID, true);
    }

    /**
     * Move the bucket with the specified ID to another dispatcher.
     * @param bucketID the ID of the bucket to move
     * @param targetDispatcher the target dispatcher to move the bucket to
     * @return the bucket moved
     * @throws NoSuchElementException if there is no bucket with the specified ID
     * @throws BucketStorageException if the maximal number of buckets is already allocated
     */
    public LocalBucket moveBucket(int bucketID, BucketDispatcher targetDispatcher) throws NoSuchElementException, BucketStorageException {
        LocalBucket bucket;
        synchronized (targetDispatcher) {
            if (targetDispatcher.getBucketCount() >= targetDispatcher.getMaxBuckets())
                throw new CapacityFullException();
            bucket = removeBucket(bucketID, false);
        }
        targetDispatcher.addBucket(bucket); // This will synchronize also on this dispatcher, thus a deadlock can occurr if two moves are cross-executed
        return bucket;
    }


    //****************** Textual representation info ******************//

    /**
     * Returns information about storage maintained by this dispatcher.
     * @return information about storage maintained by this dispatcher
     */
    @Override
    public String toString() {
        StringBuilder rtv = new StringBuilder(getClass().getName()).append(": ");
        rtv.append(getBucketCount()).append('/').append(maxBuckets).append(" buckets of ");
        rtv.append(bucketSoftCapacity).append('/').append(bucketCapacity).append(" (low ").append(bucketLowOccupation).append(")").append(bucketOccupationAsBytes?" bytes":" objects").append(" capacity");
        rtv.append(", def. ").append(defaultBucketClass.toString());
        return rtv.toString();
    }

}
