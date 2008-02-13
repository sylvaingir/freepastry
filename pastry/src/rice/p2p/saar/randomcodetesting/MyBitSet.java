package rice.p2p.saar.randomcodetesting;

//package rice.p2p.saar.blockbased;

import java.util.BitSet; 


// This is different from normal bitset with respect to providing an offset and two bitsets (negative and positive for integers less an greater than offset respectively
public class MyBitSet {

    int pivot = -1; // the offset 'boolean' value is reprsented in the index 0 of neg and pos set
    BitSet bitsetNegative; // includes all numbers strictly less than pivot
    BitSet bitsetPositive; // includes all numbers >= pivot
    

    public MyBitSet() {
	pivot = -1;
	bitsetNegative = new BitSet();
	bitsetPositive = new BitSet();	

    }


    public void set(int index) {
	if(pivot == -1) {
	    // Bitset has not been initialized
	    //System.out.println("WARNING: MyBitSet.put() invoked when pivot is -1, implicitly setting pivot");

	    pivot = index;
	    //System.out.println("WARNING: Setting pivot to " + pivot);
	}
	    
	if(index < pivot) {
	    int negOffset = (pivot - 1) - index;
	    bitsetNegative.set(negOffset);
	} else if(index >= pivot) {
	    int posOffset = index - pivot;
	    bitsetPositive.set(posOffset);
	}

    }


    public void clear(int index) {
	if(pivot == -1) {
	    return; // pivot -1 implies no bits have been set yet
	}
	    
	if(index < pivot) {
	    int negOffset = (pivot -1) - index;
	    bitsetNegative.clear(negOffset);
	   
	} else if(index >= pivot) {
	    int posOffset = index - pivot;
	    bitsetPositive.clear(posOffset);
	    
	}

    }




    public boolean get(int index) {
	if(pivot == -1) {
	    //System.out.println("WRNING: Invoking get() on uninitialized pivot in MyBitSet");      
	    return false;
	} else if(index < pivot) {
	    int negOffset = (pivot - 1) - index;
	    return bitsetNegative.get(negOffset);
	} else if(index >= pivot) {
	    int posOffset = index - pivot;
	    return bitsetPositive.get(posOffset);
	}
	return false;
    }


    public int cardinality() {
	return (bitsetNegative.cardinality() + bitsetPositive.cardinality());

    }


    // logical size in bits, as per functionality in BitSet
    public int length() {
	return (bitsetNegative.length() + bitsetPositive.length());

    }

    // actual bits consuimed in memory, as per functionality in BitSet
    public int size() {
	return (bitsetNegative.size() + bitsetPositive.size());

    }

    // Here we will create new bitsets if we have several leading zeros
    public void optimizeMemory() {
	int pivotRightShift = bitsetPositive.nextSetBit(0);
	if((bitsetNegative.cardinality() == 0) && (pivotRightShift > 256)) {
	    //String mybitsetbeforeString = this.toString();
	    bitsetNegative = new BitSet();
	    pivot = pivot + pivotRightShift;
	    BitSet newbitsetpositive = new BitSet(); 
	    for(int i=bitsetPositive.nextSetBit(0); i>=0; i=bitsetPositive.nextSetBit(i+1)) { 
		// operate on index i here
		newbitsetpositive.set(i-pivotRightShift);
	    }
	    bitsetPositive = newbitsetpositive;
	    //String mybitsetafterString = this.toString();
	    //System.out.println("MyBitSet.optimizeMemory(), before:" + mybitsetbeforeString + ", after:"+ mybitsetafterString);
	    
	}

    }

    public void initialize() {
	pivot = -1;
	bitsetNegative = new BitSet();
	bitsetPositive = new BitSet();	
    }

    public String toString() {
	String s = "MyBitSet:pivot=" + pivot + ", negLen:" + bitsetNegative.length() + ", negSet: " + bitsetNegative.toString() + ", posLen: " + bitsetPositive.length() + ", posSet: " + bitsetPositive.toString();
	return s;
    }


    
    public static void main(String[] args) throws Exception {
	
	MyBitSet bitset = new MyBitSet();
	bitset.set(75);
	System.out.println("size(75):" + bitset.size() + ", len: " + bitset.length() + ", cn: " + bitset.cardinality());
	bitset.set(100);
	System.out.println("size(75, 100):" + bitset.size() + ", len: " + bitset.length() + ", cn: " + bitset.cardinality());
	bitset.set(50);
	System.out.println("size(50, 75, 100):" + bitset.size() + ", len: " + bitset.length() + ", cn: " + bitset.cardinality());
	bitset.set(150);
	System.out.println("size(50, 75, 100, 150):" + bitset.size() + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());
	bitset.set(1000);
	System.out.println("size(50, 75, 100, 150, 1000):" + bitset.size() + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());



	System.out.println("size(50, 75, 100, 150, 1000): get(50)" + bitset.get(50) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());
	
	System.out.println("size(50, 75, 100, 150, 1000): get(2000)" + bitset.get(2000) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());
	
 	System.out.println("size(50, 75, 100, 150, 1000): get(75)" + bitset.get(75) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());

	System.out.println("size(50, 75, 100, 150, 1000): get(100)" + bitset.get(100) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());

	System.out.println("size(50, 75, 100, 150, 1000): get(50)" + bitset.get(50) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());

	System.out.println("size(50, 75, 100, 150, 1000): get(150)" + bitset.get(150) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());

	System.out.println("size(50, 75, 100, 150, 1000): get(200)" + bitset.get(200) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());

	bitset.clear(150);
	System.out.println("size(50, 75, 100, 1000):" + bitset.size() + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());
	System.out.println("String:" + bitset);


	bitset.clear(50);
	bitset.clear(75);
	System.out.println("size(100, 1000):" + bitset.size() + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());
	System.out.println("String:" + bitset);

	bitset.clear(100);
	System.out.println("size(1000):" + bitset.size() + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());
	System.out.println("String:" + bitset);


	bitset.optimizeMemory();
	System.out.println("size-optimized(1000):" + bitset.size() + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());
	System.out.println("String:" + bitset);


	System.out.println("size-optimized(1000): get(1000)" + bitset.get(1000) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());

	System.out.println("size-optimized(75): get(75)" + bitset.get(75) + ", len: " + bitset.length()+ ", cn: " + bitset.cardinality());



     }
    

    
}



