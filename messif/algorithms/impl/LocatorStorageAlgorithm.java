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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import messif.algorithms.Algorithm;
import messif.algorithms.AlgorithmMethodException;
import messif.buckets.BucketStorageException;
import messif.buckets.index.LocalAbstractObjectOrder;
import messif.buckets.storage.StorageIndexed;
import messif.buckets.storage.StorageSearch;
import messif.buckets.storage.impl.DatabaseStorage;
import messif.objects.LocalAbstractObject;
import messif.objects.nio.CachingSerializator;
import messif.operations.AbstractOperation;
import messif.operations.data.BulkInsertOperation;
import messif.operations.data.DeleteByLocatorOperation;
import messif.operations.data.DeleteOperation;
import messif.operations.data.InsertOperation;
import messif.operations.query.GetObjectByLocatorOperation;
import messif.operations.query.GetObjectsByLocatorsOperation;

/**
 * Wrapper for any {@link Algorithm} that stores all the inserted objects into additional
 * storage. The insert/delete operations are intercepted by the wrapper algorithm and
 * used to update the local storage. The modification operations are then passed the encapsulated
 * algorithm for processing.
 *
 * The {@link GetObjectByLocatorOperation} and {@link GetObjectsByLocatorsOperation} are handled
 * by the wrapper by accessing the indexed storage. All the other query operations
 * are handled by the encapsulated algorithm.
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class LocatorStorageAlgorithm extends Algorithm {
    /** Class serial id for serialization. */
    private static final long serialVersionUID = 1L;

    /** Encapsulated algorithm that handles regular queries */
    private final Algorithm algorithm;
    /** Internal indexed storage for handling locator-related queries */
    private final StorageIndexed<LocalAbstractObject> storage;

    /**
     * Creates a new locator-storage algorithm wrapper for the given algorithm.
     * @param encapsulatedAlgorithm the algorithm to wrap
     * @param storage the storage used for indexed retrieval of the objects
     * @throws IllegalArgumentException if the prototype returned by {@link #getExecutorParamClasses getExecutorParamClasses} has no items
     */
    @AlgorithmConstructor(description = "creates locator-storage wrapper for the given algorithm", arguments = {"algorithm to encapsulate", "internal locator-based indexed storage"})
    public LocatorStorageAlgorithm(Algorithm encapsulatedAlgorithm, StorageIndexed<LocalAbstractObject> storage) throws IllegalArgumentException {
        super("LocatorStorage-" + encapsulatedAlgorithm.getName());
        this.algorithm = encapsulatedAlgorithm;
        this.storage = storage;
    }

    @AlgorithmConstructor(description = "creates locator-storage wrapper for the given algorithm", arguments = {"algorithm to encapsulate", "internal locator-based indexed storage"})
    public LocatorStorageAlgorithm(Algorithm encapsulatedAlgorithm, String dbConnUrl, String tableName, Class<?>[] cacheClasses) throws IllegalArgumentException, SQLException {
        this(encapsulatedAlgorithm, new DatabaseStorage<LocalAbstractObject>(LocalAbstractObject.class, dbConnUrl, null, null, tableName, "id", getDatabaseMap(cacheClasses)));
    }

    private static Map<String, DatabaseStorage.ColumnConvertor<LocalAbstractObject>> getDatabaseMap(Class<?>[] cacheClasses) {
        Map<String, DatabaseStorage.ColumnConvertor<LocalAbstractObject>> ret = new HashMap<String, DatabaseStorage.ColumnConvertor<LocalAbstractObject>>();
        ret.put("locator", DatabaseStorage.getLocatorColumnConvertor(true, false, true));
        ret.put("binobj", new DatabaseStorage.BinarySerializableColumnConvertor<LocalAbstractObject>(LocalAbstractObject.class, new CachingSerializator<LocalAbstractObject>(LocalAbstractObject.class, cacheClasses)));
        return ret;
    }

    @Override
    public void finalize() throws Throwable {
        algorithm.finalize();
        storage.finalize();
        super.finalize(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void destroy() throws Throwable {
        algorithm.destroy();
        storage.destroy();
        super.destroy(); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Returns the encapsulated algorithm.
     * @return the encapsulated algorithm
     */
    public Algorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * Implementation of the insert operation.
     * The object is first inserted into the internal storage and if it is successful,
     * the operation is passed to the encapsulated algorithm.
     *
     * @param op the insert operation to execute
     * @throws BucketStorageException if there was a problem storing the object in the internal storage
     * @throws AlgorithmMethodException if there was a problem executing the operation on the encapsulated algorithm
     * @throws NoSuchMethodException if the encapsulated algorithm does not support the insert operation
     */
    public void insertOperation(InsertOperation op) throws BucketStorageException, AlgorithmMethodException, NoSuchMethodException {
        storage.store(op.getInsertedObject());
        algorithm.executeOperation(op);
    }

    /**
     * Implementation of the bulk-insert operation.
     * The objects are first inserted into the internal storage and if it is successful,
     * the operation is passed to the encapsulated algorithm.
     *
     * @param op the bulk-insert operation to execute
     * @throws BucketStorageException if there was a problem storing an object in the internal storage
     * @throws AlgorithmMethodException if there was a problem executing the operation on the encapsulated algorithm
     * @throws NoSuchMethodException if the encapsulated algorithm does not support the insert operation
     */
    public void bulkInsertOperation(BulkInsertOperation op) throws BucketStorageException, AlgorithmMethodException, NoSuchMethodException {
        for (LocalAbstractObject object : op.getInsertedObjects()) {
            storage.store(object);
        }
        algorithm.executeOperation(op);
    }

    /**
     * Implementation of the delete operation.
     * The object is first deleted from the encapsulated algorithm and
     * the actually deleted objects are removed (using the locator) from
     * the internal storage.
     *
     * @param op the delete operation to execute
     * @throws BucketStorageException if there was a problem removing the object from the internal storage
     * @throws AlgorithmMethodException if there was a problem executing the operation on the encapsulated algorithm
     * @throws NoSuchMethodException if the encapsulated algorithm does not support the delete operation
     */
    public void deleteOperation(DeleteOperation op) throws BucketStorageException, AlgorithmMethodException, NoSuchMethodException {
        op = algorithm.executeOperation(op);
        if (op.wasSuccessful()) {
            Set<String> locators = new HashSet<String>();
            for (LocalAbstractObject deletedObject : op.getObjects()) {
                locators.add(deletedObject.getLocatorURI());
            }
            StorageSearch<LocalAbstractObject> search = storage.search(LocalAbstractObjectOrder.locatorToLocalObjectComparator, locators);
            if (search.next())
                search.remove();
        }
    }

    /**
     * Implementation of the delete-by-locator operation.
     * The objects to delete are first identified using the internal storage.
     * Then for each such object, the {@link DeleteOperation} is called on the
     * encapsulated algorithm. If successful, the object is also removed from
     * the internal storage.
     *
     * @param op the delete-by-locator operation to execute
     * @throws BucketStorageException if there was a problem removing an object from the internal storage
     * @throws AlgorithmMethodException if there was a problem executing the operation on the encapsulated algorithm
     * @throws NoSuchMethodException if the encapsulated algorithm does not support the delete operation
     */
    public void deleteByLocatorOperation(DeleteByLocatorOperation op) throws BucketStorageException, AlgorithmMethodException, NoSuchMethodException {
        StorageSearch<LocalAbstractObject> search = storage.search(LocalAbstractObjectOrder.locatorToLocalObjectComparator, op.getLocators());
        while (!op.isLimitReached() && search.next()) {
            DeleteOperation delete = algorithm.executeOperation(new DeleteOperation(search.getCurrentObject(), op.getDeleteLimit(), true));
            if (delete.wasSuccessful()) {
                search.remove();
                op.addDeletedObject(search.getCurrentObject());
            } else {
                op.endOperation(delete.getErrorCode());
                return;
            }
        }
        op.endOperation();
    }

    /**
     * Implementation of the get-by-locator operation.
     * The object is retrieved using the internal storage.
     * @param op the get-by-locator operation to execute
     */
    public void objectByLocator(GetObjectByLocatorOperation op) {
        StorageSearch<LocalAbstractObject> search = storage.search(LocalAbstractObjectOrder.locatorToLocalObjectComparator, op.getLocator());
        while (search.next()) {
            op.addToAnswer(search.getCurrentObject());
        }
        op.endOperation();
    }

    /**
     * Implementation of the get-by-multiple-locators operation.
     * The objects are retrieved using the internal storage.
     * @param op the get-by-multiple-locators operation to execute
     */
    public void objectsByLocators(GetObjectsByLocatorsOperation op) {
        StorageSearch<LocalAbstractObject> search = storage.search(LocalAbstractObjectOrder.locatorToLocalObjectComparator, op.getLocators());
        while (search.next()) {
            op.addToAnswer(search.getCurrentObject());
        }
        op.endOperation();
    }

    /**
     * Implementation of a generic operation.
     * The the operation is passed the encapsulated algorithm for processing.
     * @param op the generic operation to execute
     * @throws AlgorithmMethodException if the operation execution on the encapsulated algorithm has thrown an exception
     * @throws NoSuchMethodException if the operation is unsupported by the encapsulated algorithm
     */
    public void processOperation(AbstractOperation op) throws AlgorithmMethodException, NoSuchMethodException {
        algorithm.executeOperation(op);
    }
}
