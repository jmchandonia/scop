/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2012-2018 The Regents of the University of California
 *
 * For feedback, mailto:scope@compbio.berkeley.edu
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * Version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */
package gov.lbl.scop.util;

import java.io.*;
import java.util.*;

/**
   Utility to efficiently compute protein coverage by features
   such as domains
*/
public class Coverage extends BitSet {
	private int setlength;

	/**
	   New coverage measurement; give protein/gene length.
	   Initial state is fully uncovered.
	*/
	public Coverage(int l) {
	    super(l);
	    setlength = l;
	}

	public Coverage(BitSet b) {
	    this(b.length());
	    or(b);
	}

	public Coverage(Coverage c) {
	    this(c.setlength);
	    or(c);
	}
    
	/**
	   Set a range as covered.  0-offset.
	   Warning: doesn't throw out of bounds exception unless start
	   or length are negative.
	*/
	public void set(int start, int length) {
	    super.set(start, start+length);
	}

	/**
	   Pct coverage, from 0 - 100.
	*/
	final public double pctCovered() {
	    if (setlength==0) return 0.0;

	    int nCovered = cardinality();
	    return ((double)nCovered / (double)setlength * 100.0);
	}

	/**
	   how many are covered, in regions of minimum length N?
	*/
	final public int nCovered(int n) {
	    if (n<=1) return cardinality();

	    int rv = 0;
	    
	    int consecCovered;
	    int j = 0;
	    while (j < setlength) {
            int ns = nextClearBit(j);
            if (ns == -1) ns = setlength;
            consecCovered = ns-j;
            if (consecCovered >= n) {
                rv += consecCovered;
            }
            if (ns < setlength) {
                j = nextSetBit(ns);
                if (j==-1) j = setlength;
            }
            else {
                j = setlength;
            }
	    }

	    return rv;
	}

	final public int nCovered() {
	    return cardinality();
	}

	/**
	   how many are uncovered, in regions of minimum length N?
	*/
	final public int nUncovered(int n) {
	    if (n<=1) return setlength-cardinality();

	    int rv = 0;
	    
	    int consecUncovered;
	    int j = 0;
	    while (j < setlength) {
            int ns = nextSetBit(j);
            if (ns == -1) ns = setlength;
            consecUncovered = ns-j;
            if (consecUncovered >= n) {
                rv += consecUncovered;
            }
            if (ns < setlength) {
                j = nextClearBit(ns);
                if (j==-1) j = setlength;
            }
            else {
                j = setlength;
            }
	    }

	    return rv;
	}

    /**
       set uncovered regions of less than length N to covered
    */
    final public void coverShort(int n) {
	    if (n<=2) {
            set(0,setlength);
            return;
        }

	    int consecUncovered;
	    int j = 0;
	    while (j < setlength) {
            int ns = nextSetBit(j);
            if (ns == -1) ns = setlength;
            consecUncovered = ns-j;
            if (consecUncovered < n) {
                set(j,consecUncovered);
            }
            if (ns < setlength) {
                j = nextClearBit(ns);
                if (j==-1) j = setlength;
            }
            else {
                j = setlength;
            }
	    }
    }

    /**
       Total number of uncovered residues
    */
	final public int nUncovered() {
	    return setlength-cardinality();
	}
	
	/**
       Length of longest uncovered stretch of protein.
	*/
	final public int longestUncovered() {
	    if (setlength==0) return 0;

	    int longestUncovered = 0;
	    int consecUncovered;
	    int j = 0;
	    while (j < setlength) {
            int ns = nextSetBit(j);
            if (ns == -1) ns = setlength;
            consecUncovered = ns-j;
            if (consecUncovered > longestUncovered) {
                longestUncovered = consecUncovered;
            }
            if (ns < setlength) {
                j = nextClearBit(ns);
                if (j==-1) j = setlength;
            }
            else {
                j = setlength;
            }
	    }
	    
	    return longestUncovered;
	}

	/**
       Length of longest covered stretch of protein.
	*/
	final public int longestCovered() {
	    if (setlength==0) return 0;

        flip(0,setlength);
        int rv = longestUncovered();
        flip(0,setlength);
        return rv;
	}
    
	/**
       Finds longest uncovered stretch of protein:  return
	   start and length
	*/
	final public int[] findLongestUncovered() {
	    int[] rv = new int[2];
	    if (setlength==0) return rv;

	    int consecUncovered;
	    int j = 0;
	    while (j < setlength) {
            int ns = nextSetBit(j);
            if (ns == -1) ns = setlength;
            consecUncovered = ns-j;
            if (consecUncovered > rv[1]) {
                rv[0] = j;
                rv[1] = consecUncovered;
            }
            if (ns < setlength) {
                j = nextClearBit(ns);
                if (j==-1) j = setlength;
            }
            else {
                j = setlength;
            }
	    }
	    
	    return rv;
	}

	/**
	   return subset
	*/
	public Coverage getSubset(int start, int length) {
	    return new Coverage(get(start, start+length));
	}

	/**
	   index of first covered residue, or -1 if none
	*/
	public int firstCovered() {
	    return nextSetBit(0);
	}

	/**
	   index of last covered residue, or -1 if none
	*/
	public int lastCovered() {
	    int lsb = -1;
	    for(int i=nextSetBit(0); i>=0; i=nextSetBit(i+1)) {
            lsb = i;
	    }
	    return lsb;
	}
}
