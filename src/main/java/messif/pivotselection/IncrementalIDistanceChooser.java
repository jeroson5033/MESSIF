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
package messif.pivotselection;

import java.io.Serializable;
import java.util.Iterator;
import messif.objects.AbstractObject;
import messif.objects.LocalAbstractObject;
import messif.objects.util.AbstractObjectIterator;
import messif.objects.util.AbstractObjectList;


/**
 * Chooses pivots according to a generalized iDistance clustering strategy.
 * 
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class IncrementalIDistanceChooser extends AbstractPivotChooser implements Serializable {
    /** Class version id for serialization */
    private static final long serialVersionUID = 1L;

    /** Size of the sample set used to test the candidate pivot (used to estimate mu_d) */
    private final int sampleSetSize;
    
    /** Size of the candidate set of pivots from which one pivot will be picked. */
    private final int samplePivotSize;
    
    /** Creates a new instance of IncrementalPivotChooser */
    public IncrementalIDistanceChooser() {
        this(10000, 100);
    }

    /**
     * Creates a new instance of IncrementalPivotChooser.
     * @param sampleSetSize the size of the sample set used to test the candidate pivot (used to estimate mu_d)
     * @param samplePivotSize the size of the candidate set of pivots from which one pivot will be picked
     */
    public IncrementalIDistanceChooser(int sampleSetSize, int samplePivotSize) {
        this.sampleSetSize = sampleSetSize;
        this.samplePivotSize = samplePivotSize;
    }

    /**
     * Select a pivot closes to <code>object</code>.
     * @param object the object for which to get the closest pivot
     * @param pivotIter pivots iterator
     * @return the closest pivot for the passed object
     */
    private LocalAbstractObject getClosestPivot(LocalAbstractObject object, Iterator<? extends AbstractObject> pivotIter) {
        float minVal = Float.MAX_VALUE;
        float tmpDist;
        LocalAbstractObject preselectedPivot = null;
        LocalAbstractObject pivotX = pivotIter.hasNext() ? ((LocalAbstractObject) pivotIter.next()):null;
        preselectedPivot = pivotX;
        for (; pivotIter.hasNext(); pivotX = (LocalAbstractObject) pivotIter.next()) {
            tmpDist = object.getDistance(pivotX);
            if (minVal > tmpDist) {
                minVal = tmpDist;
                preselectedPivot = pivotX;
            }
        }
        return preselectedPivot;
    }
    
    /**
     * Selects new pivots.
     * Implementation of the incremental pivot selection algorithm.
     * This method is not intended to be called directly. It is automatically called from getPivot(int).
     *
     * This pivot selection technique depends on previously selected pivots. The AbstractObjectList
     * with such the pivots can be passed in getPivot(position,addInfo) in addInfo parameter
     * (preferable way) or directly set using setAdditionalInfo() method.
     * If the list of pivots is not passed it is assumed that no pivots were selected.
     *
     * Statistics are maintained automatically.
     * @param pivots number of pivots to generate
     * @param objectIter Iterator over the sample set of objects to choose new pivots from
     */
    @Override
    protected void selectPivot(int pivots, AbstractObjectIterator<? extends LocalAbstractObject> objectIter) {
        // Store all passed objects temporarily
        AbstractObjectList<LocalAbstractObject> objectList = new AbstractObjectList<LocalAbstractObject>(objectIter);
        
        int sampleSize = (Math.sqrt(sampleSetSize) > objectList.size()) ? objectList.size() * objectList.size() : sampleSetSize;

        // Select objects randomly
        AbstractObjectList<LocalAbstractObject> leftPair  = objectList.randomList(sampleSize, false, new AbstractObjectList<LocalAbstractObject>());
        AbstractObjectList<LocalAbstractObject> rightPair = objectList.randomList(sampleSize, false, new AbstractObjectList<LocalAbstractObject>());
        
        
        LocalAbstractObject leftObj;
        LocalAbstractObject rightObj;
        
        LocalAbstractObject tmpPivot = null;                    // temporary pivot
        
        float[] distsLeftClosest = new float[sampleSize];      // stored distances between left objects and the best pivot
        float[] distsRightClosest = new float[sampleSize];     // stored distances between right objects and the best pivot
        
        // initialize array of distances to former pivots
        AbstractObjectIterator<LocalAbstractObject> leftIter = leftPair.iterator();
        AbstractObjectIterator<LocalAbstractObject> rightIter= rightPair.iterator();
        
        for (int i = 0; i < sampleSize; i++) {
            leftObj = leftIter.next();
            rightObj = rightIter.next();
            
            tmpPivot = getClosestPivot(leftObj, preselectedPivots.iterator());
            if (tmpPivot != null) {
                distsLeftClosest[i] = leftObj.getDistance(tmpPivot);//Math.abs(leftObj.getDistanceFastly(tmpPivot) - rightObj.getDistanceFastly(tmpPivot));
                distsRightClosest[i] = rightObj.getDistance(tmpPivot);
            } else {
                distsLeftClosest[i] = Float.MAX_VALUE;
                distsRightClosest[i] = Float.MAX_VALUE;
            }
        }
        
        
        // Select required number of pivots
        for (int p = 0; p < pivots; p++) {
            System.out.println("Selecting pivot number "+p);//", DistanceComputations: "+Statistics.printStatistics("DistanceComputations"));
            
            AbstractObjectList<LocalAbstractObject> candidatePivots = objectList.randomList(samplePivotSize, true, new AbstractObjectList<LocalAbstractObject>());
            
            float[] distsLeftToBestCand = new float[sampleSize];      // stored distances between left objects and the best pivot
            float[] distsRightToBestCand = new float[sampleSize];     // stored distances between right objects and the best pivot
            float[] distsLeftToCand = new float[sampleSize];      // stored distances between left objects and the pivot candidate
            float[] distsRightToCand = new float[sampleSize];     // stored distances between right objects and the pivot candidate
            for (int i = 0; i < sampleSize; i++) {
                distsLeftToBestCand[i] = Float.MAX_VALUE;
                distsRightToBestCand[i] = Float.MAX_VALUE;
            }
            
            float bestPivotMu = 0;                                 // mu_D of the best pivot candidate
            LocalAbstractObject bestPivot = null;                   // the best pivot candidate until now
            
            // go through all candidate pivots and compute their mu
            System.out.print("Candidates: "); int iter = 1;
            for (AbstractObjectIterator<LocalAbstractObject> pivotIter = candidatePivots.iterator(); pivotIter.hasNext(); ) {
                System.out.print(iter++ +", "); System.out.flush();
                LocalAbstractObject pivot = pivotIter.next();
                
                // compute distance between sample objects and pivot
                leftIter = leftPair.iterator();
                rightIter = rightPair.iterator();
                float mu = 0;
                for (int i = 0; i < sampleSize; i++) {
                    leftObj = leftIter.next();
                    rightObj = rightIter.next();
                    
                    //for (int i = 0; i < sampleSize; i++) {
                    float distLeftToCand = leftObj.getDistance(pivot);
                    if (distLeftToCand < distsLeftClosest[i]) {
                        distsLeftToCand[i] = distLeftToCand;
                        distsRightToCand[i] = rightObj.getDistance(pivot);
                    } else {
                        distsLeftToCand[i] = distsLeftClosest[i];
                        distsRightToCand[i] = distsRightClosest[i];
                    }
                    mu += Math.abs(distsLeftToCand[i] - distsRightToCand[i]);
                }
                mu /= (float)sampleSize;
                
                if (mu > bestPivotMu) {     // the current pivot is the best one until now, store it
                    // store mu and pivot
                    bestPivotMu = mu;
                    bestPivot = pivot;
                    // store distances from left/right objects to this pivot
                    for (int i = 0; i < sampleSize; i++) {
                        distsLeftToBestCand[i] = distsLeftToCand[i];
                        distsRightToBestCand[i] = distsRightToCand[i];
                    }
                }
            }
            System.out.println();
            // append the selected pivot
            preselectedPivots.add(bestPivot);
            // store distances from left/right objects to this pivot
            for (int i = 0; i < sampleSize; i++) {
                distsLeftClosest[i] = distsLeftToBestCand[i];
                distsRightClosest[i] = distsRightToBestCand[i];
            }
        }
    }
    
}
