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
package messif.objects.impl;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import messif.objects.LocalAbstractObject;
import messif.objects.nio.BinaryInput;
import messif.objects.nio.BinarySerializator;

/**
 * Implementation of the {@link ObjectUnsignedByteVector} with an L2 (Euclidean) metric distance.
 * 
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class ObjectUnsignedByteVectorL2 extends ObjectUnsignedByteVector {
    /** class id for serialization */
    private static final long serialVersionUID = 1L;

    //****************** Constructors ******************//

    /**
     * Creates a new instance of ObjectUnsignedByteVectorL2.
     * @param data the data content of the new object
     */
    public ObjectUnsignedByteVectorL2(short[] data) {
        super(data);
    }
    /**
     * Creates a new instance of ObjectUnsignedByteVectorL2 with randomly generated content data.
     * Content will be generated using normal distribution of random short integer numbers
     * from interval [0;max short int).
     *
     * @param dimension number of dimensions to generate
     */
    public ObjectUnsignedByteVectorL2(int dimension) {
        super(dimension);
    }

    /**
     * Creates a new instance of ObjectUnsignedByteVectorL2 with randomly generated content data.
     * Content will be generated using normal distribution of random numbers from interval
     * [min;max).
     *
     * @param dimension number of dimensions to generate
     * @param min lower bound of the random generated values (inclusive)
     * @param max upper bound of the random generated values (exclusive)
     */
    public ObjectUnsignedByteVectorL2(int dimension, short min, short max) {
        super(dimension, min, max);
    }

    /**
     * Creates a new instance of ObjectUnsignedByteVectorL2 from text stream.
     * @param stream the stream from which to read lines of text
     * @throws EOFException if the end-of-file of the given stream is reached
     * @throws IOException if there was an I/O error during reading from the stream
     * @throws NumberFormatException if a line read from the stream does not consist of comma-separated or space-separated numbers
     */
    public ObjectUnsignedByteVectorL2(BufferedReader stream) throws EOFException, IOException, NumberFormatException {
        super(stream);
    }


    //****************** Distance function ******************//

    @Override
    protected float getDistanceImpl(LocalAbstractObject obj, float distThreshold) {
        // Get access to the other object's vector data
        short[] objdata = ((ObjectUnsignedByteVector)obj).data;
        if (objdata.length != data.length)
            throw new IllegalArgumentException("Cannot compute distance on different vector dimensions (" + data.length + ", " + objdata.length + ")");
        
        double powSum = 0;
        for (int i = 0; i < data.length; i++) {
            double dif = (double) (data[i] - objdata[i]);
            powSum += dif * dif;
        }
        
        return (float)Math.sqrt(powSum);
    }


    //************ BinarySerializable interface ************//

    /**
     * Creates a new instance of ObjectUnsignedByteVectorL2 loaded from binary input buffer.
     * 
     * @param input the buffer to read the ObjectUnsignedByteVectorL2 from
     * @param serializator the serializator used to write objects
     * @throws IOException if there was an I/O error reading from the buffer
     */
    protected ObjectUnsignedByteVectorL2(BinaryInput input, BinarySerializator serializator) throws IOException {
        super(input, serializator);
    }

}
