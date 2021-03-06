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
package messif.algorithms;

import messif.network.ReplyMessage;
import messif.operations.AbstractOperation;


/**
 * Reply message for the {@link DistributedAlgorithm}.
 * The message contains the processed operation as well as the identification
 * of the message that is being replied to.
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class DistAlgReplyMessage extends ReplyMessage {
    /** Class version id for serialization */
    private static final long serialVersionUID = 1L;

    //****************** Attributes ******************//

    /** Operation processed by the algorithm */
    private final AbstractOperation operation;


    //****************** Constructors ******************//

    /**
     * Creates a new instance of DistAlgReplyMessage from the given {@link DistAlgRequestMessage}.
     * @param request the request message to reply to
     */
    public DistAlgReplyMessage(DistAlgRequestMessage request) {
        super(request);
        this.operation = request.getOperation();
    }

    /**
     * Returns the operation processed by the algorithm.
     * @return the operation processed by the algorithm
     */
    public AbstractOperation getOperation() {
        return operation;
    }


    //****************** String representation ******************//

    @Override
    public String toString() {
        return super.toString() + ": " + operation;
    }
}
