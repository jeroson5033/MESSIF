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
package messif.objects.keys;

import java.io.IOException;
import java.io.Serializable;
import messif.objects.nio.BinaryInput;
import messif.objects.nio.BinaryOutput;
import messif.objects.nio.BinarySerializable;
import messif.objects.nio.BinarySerializator;

/**
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class DoubleKeyInterval extends KeyInterval<DoubleKey> implements Serializable, BinarySerializable {
    /** class serial id for serialization */
    private static final long serialVersionUID = 1L;    
    
    /**
     * Lower bound (inclusive).
     */
    protected final DoubleKey from;
    
    /**
     * Upeer bound (inclusive).
     */
    protected final DoubleKey to;
    
    /**
     * Returns the lower bound.
     * @return the lower bound.
     */
    @Override
    public DoubleKey getFrom() {
        return from;
    }

    /**
     * Returns the upper bound.
     * @return the upper bound.
     */
    @Override
    public DoubleKey getTo() {
        return to;
    }

    /**
     * Constructor for this interval.
     * @param from lower bound (inclusive)
     * @param to upper bound (inclusive)
     */
    public DoubleKeyInterval(DoubleKey from, DoubleKey to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public int compareTo(KeyInterval<DoubleKey> o) {
        return from.compareTo(o.getFrom());
    }


    //************ BinarySerializable interface ************//

    /**
     * Creates a new instance of DoubleKeyInterval loaded from binary input.
     * 
     * @param input the input to read the DoubleKeyInterval from
     * @param serializator the serializator used to write objects
     * @throws IOException if there was an I/O error reading from the input
     */
    protected DoubleKeyInterval(BinaryInput input, BinarySerializator serializator) throws IOException {
        from = serializator.readObject(input, DoubleKey.class);
        to = serializator.readObject(input, DoubleKey.class);
    }

    /**
     * Binary-serialize this object into the <code>output</code>.
     * @param output the output that this object is binary-serialized into
     * @param serializator the serializator used to write objects
     * @return the number of bytes actually written
     * @throws IOException if there was an I/O error during serialization
     */
    @Override
    public int binarySerialize(BinaryOutput output, BinarySerializator serializator) throws IOException {
        return serializator.write(output, from) + serializator.write(output, to);
    }

    /**
     * Returns the exact size of the binary-serialized version of this object in bytes.
     * @param serializator the serializator used to write objects
     * @return size of the binary-serialized version of this object
     */
    @Override
    public int getBinarySize(BinarySerializator serializator) {
        return serializator.getBinarySize(from) + serializator.getBinarySize(to);
    }
}
