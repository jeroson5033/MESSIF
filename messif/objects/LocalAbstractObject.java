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
package messif.objects;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Map;
import messif.objects.keys.AbstractObjectKey;
import messif.objects.nio.BinaryInput;
import messif.objects.nio.BinaryOutput;
import messif.objects.nio.BinarySerializable;
import messif.objects.nio.BinarySerializator;
import messif.statistics.StatisticCounter;
import messif.statistics.Statistics;
import messif.utility.Convert;
import messif.utility.reflection.ConstructorInstantiator;
import messif.utility.reflection.NoSuchInstantiatorException;


/**
 * This class is ancestor of all objects that hold some data the MESSI Framework can work with.
 * Since MESSIF works with metric-based data, every descendant of <tt>LocalAbstractObject</tt> must
 * implement a metric function {@link #getDistanceImpl} for its own data.
 *
 * To be able to read/write data from text streams, a constructor with one {@link java.io.BufferedReader} argument
 * should be implemented to parse object data from a line of text. A dual operation should be implemented as the
 * {@link #write} method.
 *
 * Each object can hold an additional data in its {@link #suppData} attribute. However, no management is guaranteed 
 * inside MESSIF, thus, if several algorithms that use supplemental data are combined, unpredictable results might
 * appear. This attribute is never modified inside MESSIF itself apart from the {@link #clearSurplusData} method that
 * sets it to <tt>null</tt>.
 *
 * @see AbstractObject
 * @see NoDataObject
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public abstract class LocalAbstractObject extends AbstractObject {

    /** Class serial id for serialization */
    private static final long serialVersionUID = 4L;

    //****************** Attributes ******************//

    /** Supplemental data object */
    public Object suppData = null;

    /** Object for storing and using precomputed distances */
    private PrecomputedDistancesFilter distanceFilter = null;


    //****************** Statistics ******************//

    /** Global counter for distance computations (any purpose) */
    protected static final StatisticCounter counterDistanceComputations = StatisticCounter.getStatistics("DistanceComputations");

    /** Global counter for lower-bound distance computations (any purpose) */
    protected static final StatisticCounter counterLowerBoundDistanceComputations = StatisticCounter.getStatistics("DistanceComputations.LowerBound");

    /** Global counter for upper-bound distance computations (any purpose) */
    protected static final StatisticCounter counterUpperBoundDistanceComputations = StatisticCounter.getStatistics("DistanceComputations.UpperBound");


    //****************** Constructors ******************//

    /**
     * Creates a new instance of LocalAbstractObject.
     * A new unique object ID is generated and the
     * object's key is set to <tt>null</tt>.
     */
    protected LocalAbstractObject() {
        super();
    }

    /**
     * Creates a new instance of LocalAbstractObject.
     * A new unique object ID is generated and the 
     * object's key is set to the specified key.
     * @param objectKey the key to be associated with this object
     */
    protected LocalAbstractObject(AbstractObjectKey objectKey) {
        super(objectKey);
    }

    /**
     * Creates a new instance of LocalAbstractObject.
     * A new unique object ID is generated and a
     * new {@link AbstractObjectKey} is generated for
     * the specified <code>locatorURI</code>.
     * @param locatorURI the locator URI for the new object
     */
    protected LocalAbstractObject(String locatorURI) {
        super(locatorURI);
    }


    //****************** Unused/undefined, min, max distances ******************//

    /** Unknown distance constant */
    public static final float UNKNOWN_DISTANCE = Float.NEGATIVE_INFINITY;
    /** Minimal possible distance constant */
    public static final float MIN_DISTANCE = 0.0f;
    /** Maximal possible distance constant */
    public static final float MAX_DISTANCE = Float.MAX_VALUE;


    //****************** Metric functions ******************//

    /** 
     * Metric distance function.
     * Returns the distance between this object and the object that is supplied as argument.
     *
     * @param obj the object for which to measure the distance
     * @return the distance between this object and the provided object <code>obj</code>
     */
    public final float getDistance(LocalAbstractObject obj) {
        return getDistance(obj, MAX_DISTANCE);
    }

    /**
     * Metric distance function.
     *    This method is intended to be used in situations such as:
     *    We are executing a range query, so all objects distant from the query object up to the query radius
     *    must be returned. In other words, all objects farther from the query object than the query radius are
     *    uninteresting. From the distance point of view, during the evaluation process of the distance between 
     *    a pair of objects we can find out that the distance cannot be lower than a certain value. If this value 
     *    is greater than the query radius, we can safely abort the distance evaluation since we are dealing with one
     *    of those uninteresting objects.
     *
     * @param obj the object to compute distance to
     * @param distThreshold the threshold value on the distance (the query radius from the example above)
     * @return the actual distance between obj and this if the distance is lower than distThreshold.
     *         Otherwise the returned value is not guaranteed to be exact, but in this respect the returned value
     *         must be greater than the threshold distance.
     */
    public final float getDistance(LocalAbstractObject obj, float distThreshold) {
        if (distanceFilter != null && distanceFilter.isGetterSupported()) {
            float distance = distanceFilter.getPrecomputedDistance(obj);
            if (distance != UNKNOWN_DISTANCE)
                return distance;
        }

        // This check is to enhance performance when statistics are disabled
        if (Statistics.isEnabledGlobally())
            counterDistanceComputations.add();

        return getDistanceImpl(obj, distThreshold);
    }

    /**
     * Metric distance function.
     * Measures the distance between this object and <code>obj</code>.
     * The array <code>metaDistances</code> is filled with the distances
     * of the respective encapsulated objects if this object contains any, i.e.
     * this object is a descendant of {@link MetaObject}.
     *
     * <p>
     * Note that this method does not use the fast access to the
     * {@link messif.objects.PrecomputedDistancesFilter#getPrecomputedDistance precomputed distances}
     * even if there is a filter that supports it.
     * </p>
     *
     * @param obj the object to compute distance to
     * @param metaDistances the array that is filled with the distances of the respective encapsulated objects, if it is not <tt>null</tt>
     * @param distThreshold the threshold value on the distance
     * @return the actual distance between obj and this if the distance is lower than distThreshold.
     *         Otherwise the returned value is not guaranteed to be exact, but in this respect the returned value
     *         must be greater than the threshold distance.
     */
    public final float getDistance(LocalAbstractObject obj, float[] metaDistances, float distThreshold) {
        // This check is to enhance performance when statistics are disabled
        if (Statistics.isEnabledGlobally())
            counterDistanceComputations.add();

        return getDistanceImpl(obj, metaDistances, distThreshold);
    }

    /**
     * The actual implementation of the metric function (see {@link #getDistance} for full explanation).
     * The implementation should not increment distanceComputations statistics.
     *
     * @param obj the object to compute distance to
     * @param distThreshold the threshold value on the distance
     * @return the actual distance between obj and this if the distance is lower than distThreshold
     */
    protected abstract float getDistanceImpl(LocalAbstractObject obj, float distThreshold);

    /**
     * The actual implementation of the metric function that updates the distances
     * of encapsulated objects. This is required for the {@link MetaObject}
     * descendants.
     *
     * @param obj the object to compute distance to
     * @param metaDistances the array that is filled with the distances of the respective encapsulated objects, if it is not <tt>null</tt>
     * @param distThreshold the threshold value on the distance
     * @return the actual distance between obj and this if the distance is lower than distThreshold
     * @see LocalAbstractObject#getDistance
     */
    float getDistanceImpl(LocalAbstractObject obj, float[] metaDistances, float distThreshold) {
        return getDistanceImpl(obj, distThreshold);
    }

    /**
     * Returns the array that can hold distances to the respective encapsulated objects.
     * This method returns a valid array only for descendants of {@link MetaObject},
     * otherwise <tt>null</tt> is returned.
     * @return the array that can hold distances to meta distances
     */
    public float[] createMetaDistancesHolder() {
        return null;
    }

    /**
     * Normalized metric distance function, i.e. the result of {@link #getDistance}
     * divided by {@link #getMaxDistance}. Note that unless an object overrides
     * the {@link #getMaxDistance} the resulting distance will be too small.
     * 
     * @param obj the object to compute distance to
     * @param distThreshold the threshold value on the distance (see {@link #getDistance} for explanation)
     * @return the actual normalized distance between obj and this if the distance is lower than distThreshold
     */
    public final float getNormDistance(LocalAbstractObject obj, float distThreshold) {
        return getDistance(obj, distThreshold) / getMaxDistance();
    }

    /**
     * Returns a maximal possible distance for this class.
     * This method <i>must</i> return the same value for all instances of this class.
     * Default implementation returns {@link #MAX_DISTANCE}.
     * @return a maximal possible distance for this class
     */
    public float getMaxDistance() {
        return MAX_DISTANCE;
    }

    /**
     * Returns the lower bound of a metric distance.
     * More precisely, this method returns the lower bound on the distance between
     * this object and the object that is supplied as argument.
     * The function allows several levels of precision (parameter accuracy).
     *
     * @param obj the object to compute the lower-bound distance to
     * @param accuracy the level of precision to use for the lower-bound
     * @return the lower bound of the distance between this object and {@code obj}
     */
    public final float getDistanceLowerBound(LocalAbstractObject obj, int accuracy) {
        counterLowerBoundDistanceComputations.add();
        return getDistanceLowerBoundImpl(obj, accuracy);
    }

    /**
     * Implementation that actually computes the lower bound on the metric distance.
     *
     * @param obj the object to compute lower-bound distance to
     * @param accuracy the level of precision to use for lower-bound
     * @return the lower bound of the distance between this object and <code>obj</code>
     */
    protected float getDistanceLowerBoundImpl(LocalAbstractObject obj, int accuracy) {
        return MIN_DISTANCE;
    }

    /**
     * Returns the upper bound of a metric distance.
     * More precisely, this method returns the upper bound on the distance between
     * this object and the object that is supplied as argument.
     * The function allows several levels of precision (parameter accuracy).
     *
     * @param obj the object to compute the upper-bound distance to
     * @param accuracy the level of precision to use for the upper-bound
     * @return the upper bound of the distance between this object and {@code obj}
     */
    public final float getDistanceUpperBound(LocalAbstractObject obj, int accuracy) {
        counterUpperBoundDistanceComputations.add();
        return getDistanceUpperBoundImpl(obj, accuracy);
    }

    /**
     * Implementation that actually computes the upper bound on the metric distance.
     *
     * @param obj the object to compute upper-bound distance to
     * @param accuracy the level of precision to use for upper-bound
     * @return the upper bound of the distance between this object and <code>obj</code>
     */
    protected float getDistanceUpperBoundImpl(LocalAbstractObject obj, int accuracy) {
        return MAX_DISTANCE;
    }

    /**
     * Returns <tt>true</tt> if the <code>obj</code> has been excluded (filtered out) using stored precomputed distance.
     * Otherwise returns <tt>false</tt>, i.e. when <code>obj</code> must be checked using original distance (see {@link #getDistance}).
     *
     * In other words, method returns <tt>true</tt> if <code>this</code> object and <code>obj</code> are more distant than <code>radius</code>. By
     * analogy, returns <tt>false</tt> if <code>this</code> object and <code>obj</code> are within distance <code>radius</code>. However, both this cases
     * use only precomputed distances. Thus, the real distance between <code>this</code> object and <code>obj</code> can be greater
     * than <code>radius</code> although the method returned <tt>false</tt>!
     * @param obj the object to check the distance for
     * @param radius the radius between <code>this</code> object and <code>obj</code> to check
     * @return <tt>true</tt> if the <code>obj</code> has been excluded (filtered out) using stored precomputed distance
     */
    public final boolean excludeUsingPrecompDist(LocalAbstractObject obj, float radius) {
        if (distanceFilter != null && obj.distanceFilter != null)
            return distanceFilter.excludeUsingPrecompDist(obj.distanceFilter, radius);

        return false;
    }

    /**
     * Returns <tt>true</tt> if the <code>obj</code> has been included using stored precomputed distance.
     * Otherwise returns <tt>false</tt>, i.e. when <code>obj</code> must be checked using original distance (see {@link #getDistance}).
     *
     * In other words, method returns <tt>true</tt> if the distance of <code>this</code> object and <code>obj</code> is below the <code>radius</code>.
     * By analogy, returns <tt>false</tt> if <code>this</code> object and <code>obj</code> are more distant than <code>radius</code>.
     * However, both this cases use only precomputed distances. Thus, the real distance between <code>this</code> object and
     * <code>obj</code> can be lower than <code>radius</code> although the method returned <tt>false</tt>!
     * @param obj the object to check the distance for
     * @param radius the radius between <code>this</code> object and <code>obj</code> to check
     * @return <tt>true</tt> if the obj has been included using stored precomputed distance
     */
    public final boolean includeUsingPrecompDist(LocalAbstractObject obj, float radius) {
        if (distanceFilter != null && obj.distanceFilter != null)
            return distanceFilter.includeUsingPrecompDist(obj.distanceFilter, radius);

        return false;
    }


    //****************** Distance filter manipulation ******************//

    /**
     * Returns a filter of the specified class (or any of its descendants) from this object's filter chain.
     * If there is no filter with requested class, this method returns <tt>null</tt>.
     * If there are more filters of the same class, the first one is returned.
     *
     * @param <T> the class of the filter to retrieve from the chain
     * @param filterClass the class of the filter to retrieve from the chain
     * @return a filter of specified class from this object's filter chain
     * @throws NullPointerException if the filterClass is <tt>null</tt>
     */
    public final <T extends PrecomputedDistancesFilter> T getDistanceFilter(Class<T> filterClass) throws NullPointerException {
        return getDistanceFilter(filterClass, true);
    }

    /**
     * Returns a filter of the specified class from this object's filter chain.
     * If there is no filter with requested class, this method returns <tt>null</tt>.
     * If there are more filters of the same class, the first one is returned.
     *
     * @param <T> the class of the filter to retrieve from the chain
     * @param filterClass the class of the filter to retrieve from the chain
     * @param inheritable if <tt>false</tt>, the exact match of <code>filterClass</code> is required;
     *          otherwise the first filter that is assignable to <code>filterClass</code> is returned
     * @return a filter of specified class from this object's filter chain
     * @throws NullPointerException if the filterClass is <tt>null</tt>
     */
    public final <T extends PrecomputedDistancesFilter> T getDistanceFilter(Class<T> filterClass, boolean inheritable) throws NullPointerException {
        for (PrecomputedDistancesFilter currentFilter = distanceFilter; currentFilter != null; currentFilter = currentFilter.getNextFilter()) {
            Class<?> currentFilterClass = currentFilter.getClass();
            if (filterClass == currentFilterClass || (inheritable && filterClass.isAssignableFrom(currentFilterClass)))
                return filterClass.cast(currentFilter);
        }

        return null;
    }

    /**
     * Returns a filter at specified position in this object's filter chain.
     * @param position a zero based position in the chain (zero returns this filter, negative value returns the last filter)
     * @return a filter at specified position in this filter's chain
     * @throws IndexOutOfBoundsException if the specified position is too big
     */
    public final PrecomputedDistancesFilter getDistanceFilter(int position) throws IndexOutOfBoundsException {
        // Fill iteration variable
        PrecomputedDistancesFilter currentFilter = distanceFilter;
        while (currentFilter != null) {
            if (position == 0)
                return currentFilter;

            // Get next iteration value
            PrecomputedDistancesFilter nextFilter = currentFilter.getNextFilter();

            if (position < 0 && nextFilter == null)
                return currentFilter;

            currentFilter = nextFilter;
            position--;    
        }

        throw new IndexOutOfBoundsException("There is no filter at position " + position);
    }

    /**
     * Adds the specified filter to the end of this object's filter chain.
     * 
     * @param filter the filter to add to this object's filter chain
     * @param replaceIfExists if <tt>true</tt> and there is another filter with the same class as the inserted filter, it is replaced
     * @return either the replaced or the existing filter that has the same class as the newly inserted one; <tt>null</tt> is
     *         returned if the filter was appended to the end of the chain
     * @throws IllegalArgumentException if the provided chain has set nextFilter attribute
     */
    public final PrecomputedDistancesFilter chainFilter(PrecomputedDistancesFilter filter, boolean replaceIfExists) throws IllegalArgumentException {
        if (filter.nextFilter != null)
            throw new IllegalArgumentException("This filter is a part of another chain");

        // Add this filter to the object's distance filter chain
        if (distanceFilter == null) {
            // We are at the end of the chain
            distanceFilter = filter;
            return null;
        } else if (distanceFilter.getClass().equals(filter.getClass())) {
            if (!replaceIfExists)
                return distanceFilter;
            // Preserve the chain link
            filter.nextFilter = distanceFilter.nextFilter;
            // Replace filter
            PrecomputedDistancesFilter storedFilter = distanceFilter;
            distanceFilter = filter;
            return storedFilter;
        } else return distanceFilter.chainFilter(filter, replaceIfExists);
    }

    /**
     * Deletes the specified filter from this object's filter chain.
     * A concerete instance of filter is deleted (the same reference must be present in the chain).
     * 
     * @param filter the concrete instance of filter to delete from this object's filter chain
     * @return <tt>true</tt> if the filter was unchained (deleted). If the given filter was not found, <tt>false</tt> is returned.
     */
    public final boolean unchainFilter(PrecomputedDistancesFilter filter) {
        if (distanceFilter == null)
            return false;
        
        if (distanceFilter == filter) {
            distanceFilter = distanceFilter.nextFilter;
            filter.nextFilter = null;
            return true;
        } else {
            PrecomputedDistancesFilter prev = distanceFilter;
            PrecomputedDistancesFilter curr = distanceFilter.nextFilter;
            while (curr != null) {
                if (curr == filter) {
                    prev.nextFilter = curr.nextFilter;
                    filter.nextFilter = null;
                    return true;
                }
                prev = curr;
                curr = curr.nextFilter;
            }
            return false;
        }
    }

    /**
     * Destroys whole filter chain of this object.
     * The first (head of the chain) filter is returned.
     * @return the first filter in the chain; the rest of the chain can be
     *         obtained by calling {@link PrecomputedDistancesFilter#getNextFilter getNextFilter}
     */
    public final PrecomputedDistancesFilter chainDestroy() {
        PrecomputedDistancesFilter rtv = distanceFilter;
        distanceFilter = null;
        return rtv;
    }

    /**
     * Clear non-messif data stored in this object.
     * In addition to changing object key, this method removes
     * the {@link #suppData supplemental data} and
     * all {@link #distanceFilter distance filters}.
     */
    @Override
    public void clearSurplusData() {
        super.clearSurplusData();
        suppData = null;
        distanceFilter = null;
    }


    //****************** Random generators ******************//

    /**
     * Returns a pseudorandom number.
     * The generator has normal distribution not the default standardized.
     * @return a pseudorandom <code>double</code> greater than or equal 
     * to <code>0.0</code> and less than <code>1.0</code>
     */
    protected static double getRandomNormal() {
        double rand = 0;
        for (int i = 0; i < 12; i++) rand += Math.random();
        return rand/12.0;
    }

    /**
     * Returns a pseudorandom character.
     * @return a pseudorandom <code>char</code> greater than or equal 
     * to <i>a</i> and less than <i>z</i>
     */
    protected static char getRandomChar() {
        return (char)('a' + (int)(Math.random()*('z' - 'a')));
    }


    //****************** Factory ******************//

    /**
     * Provides a factory for creating instances of T from a given
     * {@link BufferedReader text stream}.
     * @param <T> the type of {@link LocalAbstractObject} to create by this factory
     */
    public static class TextStreamFactory<T extends LocalAbstractObject> {
        /** Arguments used for instantiating the object */
        private final Object[] arguments;
        /** Constructor objects of type T needed for instantiating objects */
        private final Constructor<? extends T> constructor;

        /**
         * Creates a new factory for creating instances of T from text.
         * Note that additional
         * @param objectClass the type of {@link LocalAbstractObject} to create
         * @param convertStringArguments if <tt>true</tt> the string values from the additional arguments are converted using {@link Convert#stringToType}
         * @param namedInstances map of named instances - an instance from this map is returned if the <code>string</code> matches a key in the map
         * @param additionalArguments additional arguments for the constructor of T (excluding the first {@link BufferedReader})
         */
        public TextStreamFactory(Class<? extends T> objectClass, boolean convertStringArguments, Map<String, Object> namedInstances, Object[] additionalArguments) {
            if (Modifier.isAbstract(objectClass.getModifiers()))
                throw new IllegalArgumentException("Cannot create abstract " + objectClass);
            try {
                if (additionalArguments == null || additionalArguments.length == 0) {
                    arguments = null;
                    constructor = ConstructorInstantiator.getConstructor(objectClass, true, BufferedReader.class);
                } else {
                    arguments = new Object[1 + additionalArguments.length];
                    arguments[0] = new BufferedReader(new StringReader(""));
                    System.arraycopy(additionalArguments, 0, arguments, 1, additionalArguments.length);
                    constructor = ConstructorInstantiator.getConstructor(objectClass, convertStringArguments, true, namedInstances, arguments);
                }
            } catch (NoSuchInstantiatorException e) {
                throw new IllegalArgumentException("Cannot get text stream constructor for " + objectClass + ": " + e);
            }
        }

        /**
         * Creates a new factory for creating instances of T from text.
         * @param objectClass the type of {@link LocalAbstractObject} to create
         * @param additionalArguments additional arguments for the constructor of T (excluding the first {@link BufferedReader})
         */
        public TextStreamFactory(Class<? extends T> objectClass, Object... additionalArguments) {
            this(objectClass, false, null, additionalArguments);
        }

        /**
         * Creates a new factory for creating instances of T from text.
         * The string values from the additional arguments are converted using {@link Convert#stringToType}.
         * @param objectClass the type of {@link LocalAbstractObject} to create
         * @param namedInstances map of named instances for the {@link Convert#stringToType} conversion
         * @param additionalArguments additional arguments for the constructor of T (excluding the first {@link BufferedReader})
         */
        public TextStreamFactory(Class<? extends T> objectClass, Map<String, Object> namedInstances, String... additionalArguments) {
            this(objectClass, true, namedInstances, additionalArguments);
        }

        /**
         * Sets the value of this factory's constructor argument.
         * This method can be used to change object passed to <code>additionalArguments</code>.
         *
         * @param index the parameter index to change (zero-based)
         * @param paramValue the changed value to pass to the constructor
         * @throws IllegalArgumentException when the passed object is incompatible with the constructor's parameter
         * @throws IndexOutOfBoundsException if the index parameter is out of bounds
         * @throws InstantiationException if the value passed is string that is not convertible to the constructor class
         */
        public void setConstructorParameter(int index, Object paramValue) throws IndexOutOfBoundsException, IllegalArgumentException, InstantiationException {
            if (arguments == null || index++ < 0 || index >= arguments.length) // index is incremented because the first argument is always the stream
                throw new IndexOutOfBoundsException("Invalid index (" + index + ") for " + constructor.toString());
            Class<?>[] argTypes = constructor.getParameterTypes();
            if (!argTypes[index].isInstance(paramValue)) {
                if (paramValue instanceof String)
                    paramValue = Convert.stringToType((String)paramValue, argTypes[index]);
                else
                    throw new IllegalArgumentException("Supplied object must be instance of " + argTypes[index].getName());
            }
            arguments[index] = paramValue;
        }

        /**
         * Creates a new instance of T using the text data read from the {@code dataReader}.
         * @param dataReader the text stream from which to read object's data
         * @return a new instance of T
         * @throws InvocationTargetException if there was an exception while creating the object
         */
        public T create(BufferedReader dataReader) throws InvocationTargetException {
            Object[] args = (arguments == null) ? new Object[1] : arguments.clone();
            args[0] = dataReader;
            try {
                return constructor.newInstance(args);
            } catch (InstantiationException e) {
                throw new InternalError("This should never happen: " + e);
            } catch (IllegalAccessException e) {
                throw new InternalError("This should never happen: " + e);
            }
        }

        /**
         * Creates a new instance of T using the text {@code data}.
         * @param data the text from which create the object
         * @return a new instance of T
         * @throws InvocationTargetException if there was an exception while creating the object
         */
        public T create(String data) throws InvocationTargetException {
            return create(new BufferedReader(new StringReader(data)));
        }

        /**
         * Returns the class created by this factory.
         * @return the created class
         */
        public Class<? extends T> getCreatedClass() {
            return constructor.getDeclaringClass();
        }

        @Override
        public String toString() {
            return constructor.toString();
        }
    }

    /**
     * Creates a new instance of {@code objectClass} from the {@code dataReader}.
     * The constructor of the {@link LocalAbstractObject} that reads the textual
     * data is used.
     *
     * @param <T> the class of the object to create
     * @param objectClass the class of the object to create
     * @param dataReader the buffered reader of the object data
     * @param additionalArguments more constructor arguments (in addition to first {@link BufferedReader})
     * @return a new instance of {@code objectClass}
     * @throws IllegalArgumentException if the class has is no stream constructor
     * @throws InvocationTargetException if there was an error during creating a new object instance
     */
    public static <T extends LocalAbstractObject> T create(Class<T> objectClass, BufferedReader dataReader, Object... additionalArguments) throws IllegalArgumentException, InvocationTargetException {
        return new TextStreamFactory<T>(objectClass, additionalArguments).create(dataReader);
    }

    /**
     * Creates a new LocalAbstractObject of the specified type from string.
     * The object data is feeded through string buffer stream to the stream constructor of the object.
     *
     * @param <E> the class of the object to create
     * @param objectClass the class of the object to create
     * @param objectData the string that contains object's data
     * @param additionalArguments more constructor arguments (in addition to first {@link BufferedReader})
     * @return a new instance of the specified class
     * @throws IllegalArgumentException if the specified class lacks a public <tt>BufferedReader</tt> constructor
     * @throws InvocationTargetException if there was an error during creating a new object instance
     */
    public static <E extends LocalAbstractObject> E create(Class<E> objectClass, String objectData, Object... additionalArguments) throws IllegalArgumentException, InvocationTargetException {
        return create(objectClass, new BufferedReader(new StringReader(objectData)), additionalArguments);
    }


    //****************** Clonning ******************//

    /**
     * Creates and returns a copy of this object. The precise meaning 
     * of "copy" may depend on the class of the object.
     *
     * @return a clone of this instance
     * @throws CloneNotSupportedException if the object's class does not support clonning or there was an error
     */
    @Override
    public final LocalAbstractObject clone() throws CloneNotSupportedException {
        return clone(true);
    }

    /**
     * Creates and returns a copy of this object. The precise meaning 
     * of "copy" may depend on the class of the object.
     *
     * @param cloneFilterChain the flag whether the filter chain should be clonned as well
     * @return a clone of this instance
     * @throws CloneNotSupportedException if the object's class does not support clonning or there was an error
     */
    public LocalAbstractObject clone(boolean cloneFilterChain) throws CloneNotSupportedException {
        LocalAbstractObject rtv = (LocalAbstractObject)super.clone();
        if (cloneFilterChain && rtv.distanceFilter != null)
            rtv.distanceFilter = (PrecomputedDistancesFilter)rtv.distanceFilter.clone();
        else
            rtv.distanceFilter = null;

        // Clone the supplemental data
        if ((suppData != null) && (suppData instanceof Cloneable))
            try {
                rtv.suppData = (BinarySerializable)rtv.suppData.getClass().getMethod("clone").invoke(rtv.suppData);
            } catch (IllegalAccessException e) {
                throw new CloneNotSupportedException(e.toString());
            } catch (NoSuchMethodException e) {
                throw new CloneNotSupportedException(e.toString());
            } catch (InvocationTargetException e) {
                throw new CloneNotSupportedException(e.getCause().toString());
            }
        return rtv;
    }

    /** 
     * Creates and returns a randomly modified copy of this object. 
     * The modification depends on particular subclass implementation.
     *
     * @param args any parameters required by the subclass implementation - usually two objects with 
     *        the miminal and the maximal possible values
     * @return a randomly modified clone of this instance
     * @throws CloneNotSupportedException if the object's class does not support clonning or there was an error
     */
    public LocalAbstractObject cloneRandomlyModify(Object... args) throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Object " + getClass() + " have no random modification implemented");
    }


    //****************** Data methods ******************//

    /**
     * Returns the size of this object in bytes.
     * @return the size of this object in bytes
     */
    public abstract int getSize();

    /**
     * Indicates whether some other object has the same data as this one.
     * @param   obj   the reference object with which to compare.
     * @return  <code>true</code> if this object is the same as the obj
     *          argument; <code>false</code> otherwise.
     */
    public abstract boolean dataEquals(Object obj);

    /**
     * Returns a hash code value for the data of this object.
     * @return a hash code value for the data of this object
     */
    public abstract int dataHashCode();

    /**
     * A wrapper class that allows to hash/equal abstract objects
     * using their data and not ID. Especially, standard hashing
     * structures (HashMap, etc.) can be used on wrapped object.
     */
    public static class DataEqualObject {
        /** Encapsulated object */
        protected final LocalAbstractObject object;

        /**
         * Creates a new instance of DataEqualObject wrapper over the specified LocalAbstractObject.
         * @param object the encapsulated object
         */
        public DataEqualObject(LocalAbstractObject object) {
            this.object = object;
        }

        /**
         * Returns the encapsulated object.
         * @return the encapsulated object
         */
        public LocalAbstractObject get() {
            return object;
        }

        /**
         * Returns a hash code value for the object data.
         * @return a hash code value for the data of this object
         */
        @Override
        public int hashCode() {
            return object.dataHashCode();
        }

        /**
         * Indicates whether some other object has the same data as this one.
         * @param   obj   the reference object with which to compare.
         * @return  <code>true</code> if this object is the same as the obj
         *          argument; <code>false</code> otherwise.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DataEqualObject)
                return object.dataEquals(((DataEqualObject)obj).object);
            else
                return object.dataEquals(obj);
        }
    }


    //****************** Serialization ******************//

    /**
     * Processes the comment line of text representation of the object.
     * The comment is of format "#typeOfComment class value".
     * Recognized types of comments are: <ul>
     *   <li>"#objectKey keyClass keyValue", where keyClass extends AbstractObjectKey and the comment {@link #setObjectKey(messif.objects.keys.AbstractObjectKey) sets the object key}</li>
     *   <li>"#filter filterClass filterValue", where filterClass extends PrecomputedDistancesFilter and the comment {@link #chainFilter(messif.objects.PrecomputedDistancesFilter, boolean) adds a precomputed distances filter}</li>
     * </ul>
     * @param reader the reader from which to get lines with comments
     * @return the first line that does not have the requested format (this method never returns <tt>null</tt>)
     * @throws EOFException if the last line was read
     * @throws IOException if the comment type was recognized but its value is illegal
     */
    protected String readObjectComments(BufferedReader reader) throws EOFException, IOException {
        for (;;) {
            String line = reader.readLine();
            if (line == null)
                throw new EOFException("EoF reached while initializing " + getClass().getName());

            try {
                if (line.startsWith("#objectKey ")) {
                    // Create and set the key
                    setObjectKey(Convert.stringAndClassToType(line.substring(11), ' ', AbstractObjectKey.class));
                } else if (line.startsWith("#filter ")) {
                    // Create and set the filter
                    chainFilter(Convert.stringAndClassToType(line.substring(8), ' ', PrecomputedDistancesFilter.class), false);
                } else {
                    return line;
                }
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    /**
     * Writes the object comments and data - key and filters - into an output text stream.
     * Writes the following comments: <ul>
     *   <li>"#objectKey keyClass key value", where keyClass extends AbstractObjectKey</li>
     *   <li>"#filter filterClass filter value", where filterClass extends </li>
     * </ul>
     * The data are stored by a overriden method <code>writeData</code>.
     * 
     * @param stream the stream to write the comments and data to
     * @throws java.io.IOException if any problem occures during comment writing
     */
    public final void write(OutputStream stream) throws IOException {
        write(stream, true);
    }

    /**
     * Writes the object comments and data - key and filters - into an output text stream.
     * Writes the following comments: <ul>
     *   <li>"#objectKey keyClass key value", where keyClass extends AbstractObjectKey</li>
     *   <li>"#filter filterClass filter value", where filterClass extends </li>
     * </ul>
     * The data are stored by a overriden method <code>writeData</code>.
     * 
     * @param stream the stream to write the comments and data to
     * @param writeComments if true then the comments are written
     * @throws java.io.IOException if any problem occures during comment writing
     */
    public final void write(OutputStream stream, boolean writeComments) throws IOException {
        if (writeComments) {
            // Write object key
            AbstractObjectKey key = getObjectKey();
            if (key != null)
                key.write(stream);

            // Write object distance filters
            if (distanceFilter != null)
                distanceFilter.write(stream);
        }
        writeData(stream);
    }

    /**
     * Store this object's data to a text stream.
     * This method should have the opposite deserialization in constructor of a given object class.
     *
     * @param stream the stream to store this object to
     * @throws IOException if there was an error while writing to stream
     */
    protected abstract void writeData(OutputStream stream) throws IOException;


    //************ Protected methods of BinarySerializable interface ************//

    /**
     * Creates a new instance of LocalAbstractObject loaded from binary input.
     * 
     * @param input the input to read the LocalAbstractObject from
     * @param serializator the serializator used to write objects
     * @throws IOException if there was an I/O error reading from the input
     */
    protected LocalAbstractObject(BinaryInput input, BinarySerializator serializator) throws IOException {
        super(input, serializator);
        suppData = serializator.readObject(input, Object.class);
        distanceFilter = serializator.readObject(input, PrecomputedDistancesFilter.class);
    }

    /**
     * Binary-serialize this object into the <code>output</code>.
     * @param output the output that this object is binary-serialized into
     * @param serializator the serializator used to write objects
     * @return the number of bytes actually written
     * @throws IOException if there was an I/O error during serialization
     */
    @Override
    protected int binarySerialize(BinaryOutput output, BinarySerializator serializator) throws IOException {
        return super.binarySerialize(output, serializator) +
               serializator.write(output, suppData) +
               serializator.write(output, distanceFilter);
    }

    /**
     * Returns the exact size of the binary-serialized version of this object in bytes.
     * @param serializator the serializator used to write objects
     * @return size of the binary-serialized version of this object
     */
    @Override
    protected int getBinarySize(BinarySerializator serializator) {
        return super.getBinarySize(serializator) + serializator.getBinarySize(suppData) +
                serializator.getBinarySize(distanceFilter);
    }

}
